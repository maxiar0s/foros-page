package cl.duoc.forum;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Entity
@Table(name = "FORUM_TOPIC")
public class Topic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, length = 200)
    String title;

    @Column(nullable = false, length = 4000)
    String body;

    @Column(nullable = false)
    Long authorId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id")
    Category category;

    @Column(nullable = false)
    Instant createdAt = Instant.now();

    protected Topic() {
    }
}

interface Topics extends JpaRepository<Topic, Long> {
    List<Topic> findByTitleContainingIgnoreCaseOrBodyContainingIgnoreCase(String title, String body);
}

record TopicInput(Long categoryId, @NotBlank String title, @NotBlank String body, Long userId) {
}

@RestController
@RequestMapping("/api/topics")
class TopicApi {
    private final Topics topics;
    private final Categories categories;
    private final Comments comments;

    public TopicApi(Topics topics, Categories categories, Comments comments) {
        this.topics = topics;
        this.categories = categories;
        this.comments = comments;
    }

    private Identity currentIdentity() {
        return (Identity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private boolean isAdmin() {
        return "ADMIN".equals(currentIdentity().role());
    }

    private void requireOwner(Long ownerId) {
        if (!isAdmin() && !Objects.equals(ownerId, currentIdentity().id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    @GetMapping
    List<Topic> list(@RequestParam(required = false) String search) {
        return search == null || search.isBlank() ? topics.findAll()
                : topics.findByTitleContainingIgnoreCaseOrBodyContainingIgnoreCase(search, search);
    }

    @GetMapping("/{id}")
    Topic get(@PathVariable Long id) {
        return topics.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    ResponseEntity<Topic> create(@Valid @RequestBody TopicInput input) {
        Topic topic = new Topic();
        topic.title = input.title();
        topic.body = input.body();
        topic.authorId = currentIdentity().id();
        topic.category = categories.findById(input.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.status(201).body(topics.save(topic));
    }

    @PutMapping("/{id}")
    Topic update(@PathVariable Long id, @Valid @RequestBody TopicInput input) {
        Topic topic = get(id);
        requireOwner(topic.authorId);
        topic.title = input.title();
        topic.body = input.body();
        topic.category = categories.findById(input.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return topics.save(topic);
    }

    @Transactional
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        Topic topic = get(id);
        requireOwner(topic.authorId);
        comments.deleteByTopicId(id);
        topics.delete(topic);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/comments")
    List<Comment> listComments(@PathVariable Long id) {
        get(id);
        return comments.findAll().stream().filter(comment -> comment.topic.id.equals(id)).toList();
    }

    @PostMapping("/{id}/comments")
    ResponseEntity<Comment> comment(@PathVariable Long id, @Valid @RequestBody CommentInput input) {
        Comment comment = new Comment();
        comment.topic = get(id);
        comment.authorId = currentIdentity().id();
        comment.body = input.body();
        return ResponseEntity.status(201).body(comments.save(comment));
    }

    @PutMapping("/{id}/comments/{commentId}")
    Comment editComment(@PathVariable Long id, @PathVariable Long commentId, @Valid @RequestBody CommentInput input) {
        Comment comment = findComment(id, commentId);
        requireOwner(comment.authorId);
        comment.body = input.body();
        return comments.save(comment);
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    ResponseEntity<Void> deleteComment(@PathVariable Long id, @PathVariable Long commentId) {
        Comment comment = findComment(id, commentId);
        requireOwner(comment.authorId);
        comments.delete(comment);
        return ResponseEntity.noContent().build();
    }

    private Comment findComment(Long topicId, Long commentId) {
        get(topicId);
        Comment comment = comments.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!comment.topic.id.equals(topicId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return comment;
    }
}
