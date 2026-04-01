package ai.gargantua.adapters.skill;

import ai.gargantua.autoconfigure.SkillMdParser;
import ai.gargantua.core.exception.SkillNotFoundException;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FilesystemSkillRegistry")
class FilesystemSkillRegistryTest {

    @Mock
    private SkillMdParser skillMdParser;

    @Mock
    private ResourcePatternResolver resourcePatternResolver;

    private FilesystemSkillRegistry registry;

    private static final String SKILL_PATH = "skills/";
    private static final String SKILL_CONTENT = "---\nname: test-skill\n---\nYou are a test skill.";

    @BeforeEach
    void setUp() {
        registry = new FilesystemSkillRegistry(SKILL_PATH, skillMdParser, resourcePatternResolver);
    }

    private Resource mockResource(String content) throws IOException {
        Resource resource = mock(Resource.class);
        when(resource.getInputStream())
                .thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        return resource;
    }

    private SkillMeta sampleMeta(String name) {
        return new SkillMeta(name, "Description", "1.0.0",
                true, false, "general", SkillSource.FILESYSTEM, Set.of());
    }

    @Nested
    @DisplayName("listMeta")
    class ListMeta {

        @Test
        @DisplayName("scans skill directory and returns metadata")
        void scansAndReturnsMetadata() throws IOException {
            Resource resource = mockResource(SKILL_CONTENT);
            when(resourcePatternResolver.getResources("skills/**/SKILL.md"))
                    .thenReturn(new Resource[]{resource});
            when(skillMdParser.parseToMeta(anyString(), eq(SkillSource.FILESYSTEM)))
                    .thenReturn(sampleMeta("test-skill"));

            List<SkillMeta> result = registry.listMeta();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("test-skill");
        }

        @Test
        @DisplayName("returns empty list when no skills found")
        void returnsEmptyWhenNoSkills() throws IOException {
            when(resourcePatternResolver.getResources("skills/**/SKILL.md"))
                    .thenReturn(new Resource[]{});

            List<SkillMeta> result = registry.listMeta();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("caches results on subsequent calls")
        void cachesResults() throws IOException {
            Resource resource = mockResource(SKILL_CONTENT);
            when(resourcePatternResolver.getResources("skills/**/SKILL.md"))
                    .thenReturn(new Resource[]{resource});
            when(skillMdParser.parseToMeta(anyString(), eq(SkillSource.FILESYSTEM)))
                    .thenReturn(sampleMeta("test-skill"));

            registry.listMeta();
            registry.listMeta();

            // Resource pattern resolver should only be called once due to caching
            verify(resourcePatternResolver, times(1)).getResources(anyString());
        }

        @Test
        @DisplayName("handles parse errors gracefully for individual skills")
        void handlesSingleSkillParseError() throws IOException {
            Resource goodResource = mockResource(SKILL_CONTENT);
            Resource badResource = mock(Resource.class);
            when(badResource.getInputStream()).thenThrow(new IOException("corrupt file"));
            when(badResource.getDescription()).thenReturn("bad-resource");

            when(resourcePatternResolver.getResources("skills/**/SKILL.md"))
                    .thenReturn(new Resource[]{badResource, goodResource});
            when(skillMdParser.parseToMeta(anyString(), eq(SkillSource.FILESYSTEM)))
                    .thenReturn(sampleMeta("test-skill"));

            List<SkillMeta> result = registry.listMeta();

            // Should still contain the good skill
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("handles IOException during directory scan")
        void handlesDirectoryScanError() throws IOException {
            when(resourcePatternResolver.getResources("skills/**/SKILL.md"))
                    .thenThrow(new IOException("cannot access directory"));

            List<SkillMeta> result = registry.listMeta();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("builds correct pattern when path does not end with slash")
        void buildsPatternWithoutTrailingSlash() throws IOException {
            var registryNoSlash = new FilesystemSkillRegistry(
                    "skills", skillMdParser, resourcePatternResolver);
            when(resourcePatternResolver.getResources("skills/**/SKILL.md"))
                    .thenReturn(new Resource[]{});

            registryNoSlash.listMeta();

            verify(resourcePatternResolver).getResources("skills/**/SKILL.md");
        }

        @Test
        @DisplayName("builds correct pattern when path ends with slash")
        void buildsPatternWithTrailingSlash() throws IOException {
            when(resourcePatternResolver.getResources("skills/**/SKILL.md"))
                    .thenReturn(new Resource[]{});

            registry.listMeta();

            verify(resourcePatternResolver).getResources("skills/**/SKILL.md");
        }
    }

    @Nested
    @DisplayName("load")
    class Load {

        @Test
        @DisplayName("loads full skill card for existing skill")
        void loadsSkillCard() throws IOException {
            var meta = sampleMeta("fitness-coach");
            var expectedCard = new SkillCard(meta, "You are a fitness coach",
                    List.of(), null, List.of(), null, null, null, null);

            Resource resource = mockResource(SKILL_CONTENT);
            when(resourcePatternResolver.getResources("skills/fitness-coach/SKILL.md"))
                    .thenReturn(new Resource[]{resource});
            when(skillMdParser.parseToCard(anyString(), eq(SkillSource.FILESYSTEM)))
                    .thenReturn(expectedCard);

            SkillCard result = registry.load("fitness-coach");

            assertThat(result).isNotNull();
            assertThat(result.systemPrompt()).isEqualTo("You are a fitness coach");
        }

        @Test
        @DisplayName("throws SkillNotFoundException when no resource found")
        void throwsWhenNotFound() throws IOException {
            when(resourcePatternResolver.getResources("skills/nonexistent/SKILL.md"))
                    .thenReturn(new Resource[]{});

            assertThatThrownBy(() -> registry.load("nonexistent"))
                    .isInstanceOf(SkillNotFoundException.class);
        }

        @Test
        @DisplayName("throws SkillNotFoundException on IOException")
        void throwsOnIOException() throws IOException {
            when(resourcePatternResolver.getResources("skills/broken/SKILL.md"))
                    .thenThrow(new IOException("disk error"));

            assertThatThrownBy(() -> registry.load("broken"))
                    .isInstanceOf(SkillNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findMeta")
    class FindMeta {

        @Test
        @DisplayName("returns matching skill meta")
        void returnsMatchingMeta() throws IOException {
            Resource resource = mockResource(SKILL_CONTENT);
            when(resourcePatternResolver.getResources("skills/**/SKILL.md"))
                    .thenReturn(new Resource[]{resource});
            when(skillMdParser.parseToMeta(anyString(), eq(SkillSource.FILESYSTEM)))
                    .thenReturn(sampleMeta("test-skill"));

            Optional<SkillMeta> result = registry.findMeta("test-skill");

            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo("test-skill");
        }

        @Test
        @DisplayName("returns empty when skill not found")
        void returnsEmptyWhenNotFound() throws IOException {
            when(resourcePatternResolver.getResources("skills/**/SKILL.md"))
                    .thenReturn(new Resource[]{});

            Optional<SkillMeta> result = registry.findMeta("nonexistent");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("reload")
    class Reload {

        @Test
        @DisplayName("clears cache so next listMeta re-scans")
        void clearsCacheOnReload() throws IOException {
            Resource resource = mockResource(SKILL_CONTENT);
            when(resourcePatternResolver.getResources("skills/**/SKILL.md"))
                    .thenReturn(new Resource[]{resource});
            when(skillMdParser.parseToMeta(anyString(), eq(SkillSource.FILESYSTEM)))
                    .thenReturn(sampleMeta("test-skill"));

            registry.listMeta();
            registry.reload();
            registry.listMeta();

            // Should be called twice: once before reload and once after
            verify(resourcePatternResolver, times(2)).getResources(anyString());
        }
    }
}
