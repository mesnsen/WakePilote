package wakepilote.gradlelogic;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.transform.*;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.zip.*;

/** Reproducible privacy patch for the one pinned upstream AAR; native code is unchanged. */
@CacheableTransform
public abstract class OfflineMediaPipe implements TransformAction<TransformParameters.None> {
    private static final String SHA = "f4bcadaab6ae07e2ac2b4710da9f0b193603990f06832a8aefd81215b791981b";
    private static final String PREFIX = "com/google/mediapipe/tasks/core/logging/";
    private static final Set<String> REMOVED = Set.of(PREFIX + "TasksStatsProtoLogger.class", PREFIX + "RemoteLoggingClient.class");

    @InputArtifact @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override public void transform(TransformOutputs outputs) {
        File input = getInputArtifact().get().getAsFile();
        if (!input.getName().startsWith("tasks-core-")) {
            outputs.file(input);
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(input.toPath());
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!hash.equals(SHA)) throw new GradleException("MediaPipe AAR changed: privacy patch must be reviewed");
            Files.write(outputs.file("tasks-core-offline.aar").toPath(), rewrite(bytes, false));
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            throw new GradleException("Cannot apply MediaPipe privacy patch", e);
        }
    }

    private static byte[] rewrite(byte[] bytes, boolean classes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int removed = 0, factories = 0, jars = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes));
             ZipOutputStream out = new ZipOutputStream(buffer)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] data = in.readAllBytes();
                if (classes && REMOVED.contains(name)) { removed++; continue; }
                if (!classes && name.equals("classes.jar")) { data = rewrite(data, true); jars++; }
                if (classes && name.equals(PREFIX + "TasksStatsLoggerFactory.class")) {
                    // Both ASCII names have the same byte length: constant-pool offsets stay intact.
                    String original = new String(data, StandardCharsets.ISO_8859_1);
                    int occurrences = original.split("TasksStatsProtoLogger", -1).length - 1;
                    if (occurrences != 2) throw new IOException("Unexpected logger factory bytecode");
                    data = original.replace("TasksStatsProtoLogger", "TasksStatsDummyLogger")
                        .getBytes(StandardCharsets.ISO_8859_1);
                    factories++;
                }
                if (classes) {
                    String content = new String(data, StandardCharsets.ISO_8859_1);
                    if (content.contains("TasksStatsProtoLogger") || content.contains("RemoteLoggingClient") ||
                        content.contains("com/google/android/datatransport"))
                        throw new IOException("Unexpected remaining telemetry reference: " + name);
                }
                ZipEntry copy = new ZipEntry(name);
                copy.setTime(0); // Deterministic output, independent of the build machine clock.
                out.putNextEntry(copy); out.write(data); out.closeEntry();
            }
        }
        if (classes && (removed != 2 || factories != 1)) throw new IOException("Incomplete privacy patch");
        if (!classes && jars != 1) throw new IOException("Unexpected AAR structure");
        return buffer.toByteArray();
    }
}
