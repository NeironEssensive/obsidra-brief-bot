package dev.obsidra.briefbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import dev.obsidra.briefbot.dialog.Draft;
import dev.obsidra.briefbot.dialog.DraftStore;
import dev.obsidra.briefbot.dialog.Step;
import dev.obsidra.briefbot.lead.Asset;
import dev.obsidra.briefbot.lead.Deadline;
import dev.obsidra.briefbot.lead.Lead;
import dev.obsidra.briefbot.lead.LeadLimitExceededException;
import dev.obsidra.briefbot.lead.LeadRepository;
import dev.obsidra.briefbot.lead.LeadRequest;
import dev.obsidra.briefbot.lead.LeadService;
import dev.obsidra.briefbot.lead.LeadStats;
import dev.obsidra.briefbot.lead.LeadStatus;
import dev.obsidra.briefbot.lead.ProjectType;

/** Весь контекст приложения против настоящих PostgreSQL и Redis в Docker; к Telegram не подключается. */
@SpringBootTest(properties = {"bot.token=test-token", "bot.polling-enabled=false", "bot.admin-chat-id=0"})
@Testcontainers
class BriefBotIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private LeadService leads;

    @Autowired
    private LeadRepository repository;

    @Autowired
    private DraftStore drafts;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void draftIsStoredInRedisWithTtl() {
        Draft draft = new Draft(100L);
        draft.setStep(Step.BUDGET);
        draft.setType(ProjectType.GAME);
        draft.setDescription("Промо-игра для бренда");
        draft.toggleAsset(Asset.SPEC);
        draft.toggleAsset(Asset.DESIGN);

        drafts.save(draft);
        Draft loaded = drafts.find(100L).orElseThrow();

        assertThat(loaded.getStep()).isEqualTo(Step.BUDGET);
        assertThat(loaded.getType()).isEqualTo(ProjectType.GAME);
        assertThat(loaded.getDescription()).isEqualTo("Промо-игра для бренда");
        assertThat(loaded.getAssets()).containsExactlyInAnyOrder(Asset.SPEC, Asset.DESIGN);
        assertThat(loaded.getBudget()).isNull();
        assertThat(redisTemplate.getExpire("brief:draft:100"))
                .isPositive()
                .isLessThanOrEqualTo(Duration.ofHours(24).toSeconds());

        drafts.delete(100L);
        assertThat(drafts.find(100L)).isEmpty();
    }

    @Test
    void submittedLeadIsStoredWithNumberAndAssets() {
        Lead lead = leads.submit(request(200L, ProjectType.BOT));

        Lead saved = repository.findById(lead.getId()).orElseThrow();
        assertThat(saved.getId()).isGreaterThanOrEqualTo(1001L);
        assertThat(saved.getProjectType()).isEqualTo(ProjectType.BOT);
        assertThat(saved.getAssets()).containsExactlyInAnyOrder(Asset.SPEC, Asset.REFERENCES);
        assertThat(saved.getStatus()).isEqualTo(LeadStatus.NEW);
    }

    @Test
    void dailyLimitIsCountedPerUser() {
        for (int i = 0; i < 3; i++) {
            leads.submit(request(300L, ProjectType.SITE));
        }

        assertThatThrownBy(() -> leads.submit(request(300L, ProjectType.SITE)))
                .isInstanceOf(LeadLimitExceededException.class);
        assertThatCode(() -> leads.submit(request(301L, ProjectType.SITE))).doesNotThrowAnyException();
    }

    @Test
    void statsGroupLeadsByTypeAndStatus() {
        Lead first = leads.submit(request(400L, ProjectType.BOT));
        leads.submit(request(401L, ProjectType.BOT));
        leads.submit(request(402L, ProjectType.SITE));
        leads.changeStatus(first.getId(), LeadStatus.IN_PROGRESS);

        LeadStats stats = leads.statsFor(Duration.ofDays(7));

        assertThat(stats.total()).isEqualTo(3);
        assertThat(stats.byType()).containsEntry(ProjectType.BOT, 2L).containsEntry(ProjectType.SITE, 1L);
        assertThat(stats.byStatus()).containsEntry(LeadStatus.NEW, 2L).containsEntry(LeadStatus.IN_PROGRESS, 1L);
    }

    private static LeadRequest request(long userId, ProjectType type) {
        return new LeadRequest(userId, "client" + userId, "Клиент", type, "Нужен проект для теста",
                Set.of(Asset.SPEC, Asset.REFERENCES), "20–50 тыс. ₽", Deadline.MONTH, "@client" + userId);
    }
}
