package me.skerik.aifriend;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.RemoteRepository;
import org.jspecify.annotations.NullMarked;

/** Downloads the libraries listed in libraries.txt at the first start instead of shading them into the jar. */
@NullMarked
public final class AIFriendLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        List<String> libraries = readLibraries();
        if (libraries.isEmpty()) return;
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        // slf4j-api is provided by the server; a second copy would only cause classloader confusion
        List<Exclusion> excluded = List.of(new Exclusion("org.slf4j", "slf4j-api", "*", "*"));
        for (String coordinates : libraries) {
            resolver.addDependency(new Dependency(new DefaultArtifact(coordinates), null, null, excluded));
        }
        classpathBuilder.addLibrary(resolver);
    }

    private static List<String> readLibraries() {
        try (InputStream in = AIFriendLoader.class.getResourceAsStream("/libraries.txt")) {
            if (in == null) throw new IllegalStateException("libraries.txt missing from the jar");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
