package ai.gargantua.adapters.skill;

import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HotReloadSkillRegistry implements SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(HotReloadSkillRegistry.class);

    private final SkillRegistry delegate;
    private final Path watchPath;
    private final ApplicationEventPublisher eventPublisher;
    private volatile WatchService watchService;
    private volatile Thread watchThread;
    private volatile boolean running;
    private final Map<WatchKey, Path> keyPathMap = new HashMap<>();

    public HotReloadSkillRegistry(SkillRegistry delegate, Path watchPath,
                                  ApplicationEventPublisher eventPublisher) {
        this.delegate = delegate;
        this.watchPath = watchPath;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void startWatching() {
        if (!Files.isDirectory(watchPath)) {
            log.warn("Skill watch path does not exist or is not a directory: {}", watchPath);
            return;
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerAll(watchPath);
            running = true;
            watchThread = Thread.ofVirtual().name("skill-hot-reload").start(this::watchLoop);
            log.info("Hot-reload watching: {}", watchPath);
        } catch (IOException e) {
            log.error("Failed to start skill hot-reload watcher", e);
        }
    }

    @PreDestroy
    public void stopWatching() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("Error closing watch service", e);
            }
        }
        if (watchThread != null) {
            watchThread.interrupt();
        }
    }

    private void registerAll(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                WatchKey key = dir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                keyPathMap.put(key, dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | java.nio.file.ClosedWatchServiceException e) {
                break;
            }

            Path dir = keyPathMap.get(key);
            if (dir == null) {
                key.reset();
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                Path changed = dir.resolve(pathEvent.context());

                if (changed.getFileName().toString().equals("SKILL.md")) {
                    String skillName = extractSkillName(changed);
                    log.info("Skill file changed: {} ({})", skillName, kind.name());
                    delegate.reload();
                    eventPublisher.publishEvent(new SkillReloadedEvent(this, skillName));
                }

                if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changed)) {
                    try {
                        registerAll(changed);
                    } catch (IOException e) {
                        log.warn("Failed to register new directory for watching: {}", changed, e);
                    }
                }
            }

            boolean valid = key.reset();
            if (!valid) {
                keyPathMap.remove(key);
                if (keyPathMap.isEmpty()) {
                    break;
                }
            }
        }
    }

    private String extractSkillName(Path skillMdPath) {
        Path parent = skillMdPath.getParent();
        return parent != null ? parent.getFileName().toString() : "unknown";
    }

    @Override
    public List<SkillMeta> listMeta() {
        return delegate.listMeta();
    }

    @Override
    public SkillCard load(String skillName) {
        return delegate.load(skillName);
    }

    @Override
    public Optional<SkillMeta> findMeta(String skillName) {
        return delegate.findMeta(skillName);
    }

    @Override
    public void reload() {
        delegate.reload();
    }
}
