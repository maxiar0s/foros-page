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
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Entity
@Table(name = "FORUM_COMMENT")
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "topic_id")
    Topic topic;

    @Column(nullable = false)
    Long authorId;

    @Column(nullable = false, length = 2000)
    String body;

    @Column(nullable = false)
    Instant createdAt = Instant.now();

    protected Comment() {
    }
}

interface Comments extends JpaRepository<Comment, Long> {
    void deleteByTopicId(Long topicId);
}

record CommentInput(@NotBlank String body, Long userId) {
}
