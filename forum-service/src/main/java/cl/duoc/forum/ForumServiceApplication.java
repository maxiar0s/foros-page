package cl.duoc.forum;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.persistence.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@SpringBootApplication
public class ForumServiceApplication {
 public static void main(String[] args){SpringApplication.run(ForumServiceApplication.class,args);}
 @Bean RestTemplate restTemplate(RestTemplateBuilder b){return b.build();}
 @Bean SecurityFilterChain security(HttpSecurity http, IdentityClient client) throws Exception {return http.csrf(c->c.disable()).authorizeHttpRequests(a->a.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll().anyRequest().authenticated()).exceptionHandling(e->e.authenticationEntryPoint((r,s,x)->s.sendError(401)).accessDeniedHandler((r,s,x)->s.sendError(403))).addFilterBefore(new BearerFilter(client),org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class).build();}
}

record Identity(Long id,String username,String role) {}
@Service class IdentityClient {
 private final RestTemplate http; @org.springframework.beans.factory.annotation.Value("${users-service.url:http://users-service:8081}") String base;
 IdentityClient(RestTemplate http){this.http=http;}
 public Identity authenticate(String token){try {return http.exchange(base+"/api/auth/me",HttpMethod.GET,new HttpEntity<>(auth(token)),Identity.class).getBody();}catch(RestClientException e){throw new BadCredentialsException("Identity validation failed",e);}}
 private HttpHeaders auth(String t){HttpHeaders h=new HttpHeaders();h.setBearerAuth(t);return h;}
}
class BearerFilter extends OncePerRequestFilter {
 private final IdentityClient client;
 BearerFilter(IdentityClient client){this.client=client;}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,java.io.IOException {
  String h=req.getHeader("Authorization"); if(h==null||!h.startsWith("Bearer ")||h.length()==7){res.sendError(401);return;}
  try {Identity i=client.authenticate(h.substring(7)); if(i==null||i.id()==null||!("USER".equals(i.role())||"ADMIN".equals(i.role())))throw new BadCredentialsException("Invalid identity"); var a=new UsernamePasswordAuthenticationToken(i, h, List.of(new SimpleGrantedAuthority("ROLE_"+i.role())));SecurityContextHolder.getContext().setAuthentication(a);chain.doFilter(req,res);}catch(AuthenticationException e){SecurityContextHolder.clearContext();res.sendError(401);}
 }
}
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Entity @Table(name="FORUM_CATEGORY") class Category { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(nullable=false,unique=true,length=100) String name; protected Category(){} Category(String n){name=n;} }
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Entity @Table(name="FORUM_TOPIC") class Topic { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(nullable=false,length=200) String title; @Column(nullable=false,length=4000) String body; @Column(nullable=false) Long authorId; @ManyToOne(optional=false) @JoinColumn(name="category_id") Category category; @Column(nullable=false) Instant createdAt=Instant.now(); protected Topic(){} }
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Entity @Table(name="FORUM_COMMENT") class Comment { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @ManyToOne(optional=false) @JoinColumn(name="topic_id") Topic topic; @Column(nullable=false) Long authorId; @Column(nullable=false,length=2000) String body; @Column(nullable=false) Instant createdAt=Instant.now(); protected Comment(){} }
interface Categories extends JpaRepository<Category,Long>{}
interface Topics extends JpaRepository<Topic,Long>{List<Topic> findByTitleContainingIgnoreCaseOrBodyContainingIgnoreCase(String title,String body);}
interface Comments extends JpaRepository<Comment,Long>{void deleteByTopicId(Long topicId);}
record CategoryInput(@NotBlank String name){} record TopicInput(Long categoryId,@NotBlank String title,@NotBlank String body,Long userId){} record CommentInput(@NotBlank String body,Long userId){}
@RestController @RequestMapping("/api/categories") class CategoryApi {
 private final Categories repo; CategoryApi(Categories r){repo=r;} private Identity who(){return (Identity)SecurityContextHolder.getContext().getAuthentication().getPrincipal();} private void admin(){if(!"ADMIN".equals(who().role()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
 @GetMapping List<Category> all(){return repo.findAll();} @GetMapping("/{id}") Category get(@PathVariable Long id){return repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
 @PostMapping ResponseEntity<Category> add(@Valid @RequestBody CategoryInput i){admin();return ResponseEntity.status(201).body(repo.save(new Category(i.name())));}
 @PutMapping("/{id}") Category update(@PathVariable Long id,@Valid @RequestBody CategoryInput i){admin();Category c=get(id);c.name=i.name();return repo.save(c);}
 @DeleteMapping("/{id}") ResponseEntity<Void> delete(@PathVariable Long id){admin();if(!repo.existsById(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND);repo.deleteById(id);return ResponseEntity.noContent().build();}
}
@RestController @RequestMapping("/api/topics") class TopicApi {
 private final Topics topics;private final Categories categories;private final Comments comments;TopicApi(Topics t,Categories c,Comments m){topics=t;categories=c;comments=m;}
 Identity who(){return (Identity)SecurityContextHolder.getContext().getAuthentication().getPrincipal();} boolean admin(){return "ADMIN".equals(who().role());} void owner(Long id){if(!admin()&&!Objects.equals(id,who().id()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
 @GetMapping List<Topic> list(@RequestParam(required=false) String search){return search==null||search.isBlank()?topics.findAll():topics.findByTitleContainingIgnoreCaseOrBodyContainingIgnoreCase(search,search);}
 @GetMapping("/{id}") Topic get(@PathVariable Long id){return topics.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
 @PostMapping ResponseEntity<Topic> create(@Valid @RequestBody TopicInput i){Topic t=new Topic();t.title=i.title();t.body=i.body();t.authorId=who().id();t.category=categories.findById(i.categoryId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));return ResponseEntity.status(201).body(topics.save(t));}
 @PutMapping("/{id}") Topic update(@PathVariable Long id,@Valid @RequestBody TopicInput i){Topic t=get(id);owner(t.authorId);t.title=i.title();t.body=i.body();t.category=categories.findById(i.categoryId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));return topics.save(t);}
 @Transactional @DeleteMapping("/{id}") ResponseEntity<Void> delete(@PathVariable Long id){Topic t=get(id);owner(t.authorId);comments.deleteByTopicId(id);topics.delete(t);return ResponseEntity.noContent().build();}
 @GetMapping("/{id}/comments") List<Comment> listComments(@PathVariable Long id){get(id);return comments.findAll().stream().filter(c->c.topic.id.equals(id)).toList();}
 @PostMapping("/{id}/comments") ResponseEntity<Comment> comment(@PathVariable Long id,@Valid @RequestBody CommentInput i){Comment c=new Comment();c.topic=get(id);c.authorId=who().id();c.body=i.body();return ResponseEntity.status(201).body(comments.save(c));}
 @PutMapping("/{id}/comments/{commentId}") Comment editComment(@PathVariable Long id,@PathVariable Long commentId,@Valid @RequestBody CommentInput i){Comment c=comment(id,commentId);owner(c.authorId);c.body=i.body();return comments.save(c);}
 @DeleteMapping("/{id}/comments/{commentId}") ResponseEntity<Void> deleteComment(@PathVariable Long id,@PathVariable Long commentId){Comment c=comment(id,commentId);owner(c.authorId);comments.delete(c);return ResponseEntity.noContent().build();}
 Comment comment(Long tid,Long cid){get(tid);Comment c=comments.findById(cid).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));if(!c.topic.id.equals(tid))throw new ResponseStatusException(HttpStatus.NOT_FOUND);return c;}
}
