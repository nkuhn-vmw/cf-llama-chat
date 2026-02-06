package com.example.cfchat.repository;

import com.example.cfchat.model.Conversation;
import com.example.cfchat.model.Message;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ConversationRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ConversationRepository conversationRepository;

    @Test
    void findByUserIdOrderByUpdatedAtDesc_returnsUserConversations() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        entityManager.persistAndFlush(Conversation.builder()
                .title("User Conv 1").userId(userId).modelProvider("openai").build());
        entityManager.persistAndFlush(Conversation.builder()
                .title("User Conv 2").userId(userId).modelProvider("openai").build());
        entityManager.persistAndFlush(Conversation.builder()
                .title("Other Conv").userId(otherUserId).modelProvider("openai").build());

        List<Conversation> result = conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(c -> c.getUserId().equals(userId));
    }

    @Test
    void findByIdWithMessages_loadsMessagesEagerly() {
        Conversation conv = Conversation.builder()
                .title("With Messages")
                .modelProvider("openai")
                .build();
        entityManager.persistAndFlush(conv);

        Message msg1 = Message.builder()
                .role(Message.MessageRole.USER)
                .content("Hello")
                .conversation(conv)
                .build();
        entityManager.persistAndFlush(msg1);

        Message msg2 = Message.builder()
                .role(Message.MessageRole.ASSISTANT)
                .content("Hi there!")
                .conversation(conv)
                .build();
        entityManager.persistAndFlush(msg2);

        entityManager.clear(); // Clear cache to force DB fetch

        Optional<Conversation> found = conversationRepository.findByIdWithMessages(conv.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getMessages()).hasSize(2);
    }

    @Test
    void findByIdAndUserIdWithMessages_wrongUser_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        UUID wrongUserId = UUID.randomUUID();

        Conversation conv = Conversation.builder()
                .title("Private Conv")
                .userId(userId)
                .modelProvider("openai")
                .build();
        entityManager.persistAndFlush(conv);

        Optional<Conversation> found = conversationRepository.findByIdAndUserIdWithMessages(conv.getId(), wrongUserId);

        assertThat(found).isEmpty();
    }

    @Test
    void findByIdAndUserIdWithMessages_correctUser_returnsConversation() {
        UUID userId = UUID.randomUUID();

        Conversation conv = Conversation.builder()
                .title("My Conv")
                .userId(userId)
                .modelProvider("openai")
                .build();
        entityManager.persistAndFlush(conv);

        Optional<Conversation> found = conversationRepository.findByIdAndUserIdWithMessages(conv.getId(), userId);

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("My Conv");
    }

    @Test
    void countByUserId_returnsCorrectCount() {
        UUID userId = UUID.randomUUID();

        entityManager.persistAndFlush(Conversation.builder()
                .title("Conv 1").userId(userId).modelProvider("openai").build());
        entityManager.persistAndFlush(Conversation.builder()
                .title("Conv 2").userId(userId).modelProvider("openai").build());
        entityManager.persistAndFlush(Conversation.builder()
                .title("Conv 3").userId(userId).modelProvider("openai").build());

        long count = conversationRepository.countByUserId(userId);

        assertThat(count).isEqualTo(3);
    }

    @Test
    void deleteByUserId_deletesAllUserConversations() {
        UUID userId = UUID.randomUUID();

        entityManager.persistAndFlush(Conversation.builder()
                .title("Conv 1").userId(userId).modelProvider("openai").build());
        entityManager.persistAndFlush(Conversation.builder()
                .title("Conv 2").userId(userId).modelProvider("openai").build());

        conversationRepository.deleteByUserId(userId);
        entityManager.flush();

        long count = conversationRepository.countByUserId(userId);
        assertThat(count).isZero();
    }

    @Test
    void searchByTitle_findsMatching() {
        entityManager.persistAndFlush(Conversation.builder()
                .title("Python programming help").modelProvider("openai").build());
        entityManager.persistAndFlush(Conversation.builder()
                .title("Java debugging session").modelProvider("openai").build());

        List<Conversation> results = conversationRepository.searchByTitle("Python");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).contains("Python");
    }

    @Test
    void findAllByOrderByUpdatedAtDesc_returnsAll() {
        entityManager.persistAndFlush(Conversation.builder()
                .title("Conv A").modelProvider("openai").build());
        entityManager.persistAndFlush(Conversation.builder()
                .title("Conv B").modelProvider("openai").build());

        List<Conversation> all = conversationRepository.findAllByOrderByUpdatedAtDesc();

        assertThat(all).hasSizeGreaterThanOrEqualTo(2);
    }
}
