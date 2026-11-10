package cl.duoc.forum;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Entity
@Table(name = "FORUM_CATEGORY")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, unique = true, length = 100)
    String name;

    protected Category() {
    }

    Category(String name) {
        this.name = name;
    }
}

interface Categories extends JpaRepository<Category, Long> {
}

record CategoryInput(@NotBlank String name) {
}

@RestController
@RequestMapping("/api/categories")
class CategoryApi {
    private final Categories categories;

    public CategoryApi(Categories categories) {
        this.categories = categories;
    }

    private Identity currentIdentity() {
        return (Identity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private void requireAdmin() {
        if (!"ADMIN".equals(currentIdentity().role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    @GetMapping
    List<Category> all() {
        return categories.findAll();
    }

    @GetMapping("/{id}")
    Category get(@PathVariable Long id) {
        return categories.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    ResponseEntity<Category> add(@Valid @RequestBody CategoryInput input) {
        requireAdmin();
        return ResponseEntity.status(201).body(categories.save(new Category(input.name())));
    }

    @PutMapping("/{id}")
    Category update(@PathVariable Long id, @Valid @RequestBody CategoryInput input) {
        requireAdmin();
        Category category = get(id);
        category.name = input.name();
        return categories.save(category);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        requireAdmin();
        if (!categories.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        categories.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
