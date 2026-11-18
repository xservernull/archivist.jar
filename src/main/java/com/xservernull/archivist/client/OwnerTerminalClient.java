package com.xservernull.archivist.client;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.events.RubyNet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * the rd132011 owner terminal
 */
public final class OwnerTerminalClient {
    private OwnerTerminalClient() {}

    private static final Logger LOG = LogUtils.getLogger();
    private static final String JAR = "/assets/archivist/binary/OwnerTerminal.jar";
    private static final String SONATA = "/assets/archivist/binary/safetyprotocolsonata.ogg";
    private static final String ART = "/assets/archivist/binary/safetyprotocol_ascii.txt";
    private static final int ART_SECONDS = 35;
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static Process process;
    private static boolean hookAdded;

    public static synchronized void launch() {
        if (process != null && process.isAlive()) return;
        ServerSocket socket = null;
        try {
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), Archivist.MOD_ID);
            Files.createDirectories(dir);
            Path jar = dir.resolve("ownerterminal.jar");
            try (InputStream in = OwnerTerminalClient.class.getResourceAsStream(JAR)) {
                if (in == null) throw new FileNotFoundException(JAR);
                byte[] bytes = in.readAllBytes();
                if (!Files.exists(jar) || !Arrays.equals(Files.readAllBytes(jar), bytes)) Files.write(jar, bytes);
            }
            String java = ProcessHandle.current().info().command()
                    .orElseThrow(() -> new IOException("could not find the java executable"));
            socket = new ServerSocket(0);
            ProcessBuilder builder = new ProcessBuilder(java, "-Djava.awt.headless=false", "-jar", jar.toAbsolutePath().toString(), Integer.toString(socket.getLocalPort()));
            builder.redirectErrorStream(true).redirectOutput(dir.resolve("ownerterminal.log").toFile());
            process = builder.start();
            if (!hookAdded) {
                hookAdded = true;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    Process p = process;
                    if (p != null) p.destroy();
                }));
            }
            ServerSocket listening = socket;
            Thread thread = new Thread(() -> listen(listening), "archivist-ownerterminal");
            thread.setDaemon(true);
            thread.start();
            LOG.info("[terminal] window started (pid {})", process.pid());
        } catch (IOException | RuntimeException e) {
            LOG.error("[terminal] could not open the window: {}", e.toString());
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
            process = null;
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal("[terminal] could not open the window, see the log"), false);
            }
        }
    }

    /**
     * while the terminal spams
     */
    private static void playSequence() {
        try {
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), Archivist.MOD_ID);
            Path sonata = extract(SONATA, dir.resolve("safetyprotocolsonata.ogg"));
            if (sonata != null) {
                String os = System.getProperty("os.name", "").toLowerCase();
                if (os.contains("win")) new ProcessBuilder("cmd", "/c", "start", "", sonata.toAbsolutePath().toString()).start();
                else new ProcessBuilder(os.contains("mac") ? "open" : "xdg-open", sonata.toAbsolutePath().toString()).start();
            }
            Path art = extract(ART, dir.resolve("safetyprotocol_ascii.txt"));
            if (art != null && System.getProperty("os.name", "").toLowerCase().contains("win")) {
                Path script = dir.resolve("safetyprotocol.ps1");
                String path = art.toAbsolutePath().toString().replace("'", "''");
                Files.writeString(script, String.join("\r\n",
                        "$ErrorActionPreference = 'SilentlyContinue'",
                        "$Host.UI.RawUI.WindowTitle = 'safetyprotocol'",
                        "$Host.UI.RawUI.BufferSize = New-Object System.Management.Automation.Host.Size(127, 90)",
                        "$Host.UI.RawUI.WindowSize = New-Object System.Management.Automation.Host.Size(127, 74)",
                        "[Console]::CursorVisible = $false",
                        "Clear-Host",
                        "Write-Host (Get-Content -Raw -LiteralPath '" + path + "') -NoNewline",
                        "Start-Sleep -Seconds " + ART_SECONDS, ""));
                new ProcessBuilder("cmd", "/c", "start", "", "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                        "-File", script.toAbsolutePath().toString()).start();
            }
        } catch (IOException | RuntimeException e) {
            LOG.error("[terminal] sonata / ascii window failed: {}", e.toString());
        }
    }

    private static Path extract(String resource, Path target) throws IOException {
        try (InputStream in = OwnerTerminalClient.class.getResourceAsStream(resource)) {
            if (in == null) {
                LOG.warn("[terminal] {} is not in the mod, skipped", resource);
                return null;
            }
            byte[] bytes = in.readAllBytes();
            if (!Files.exists(target) || !Arrays.equals(Files.readAllBytes(target), bytes)) Files.write(target, bytes);
            return target;
        }
    }

    private static void listen(ServerSocket server) {
        try (server) {
            server.setSoTimeout(CONNECT_TIMEOUT_MS);
            Socket accepted = server.accept();
            while (!accepted.getInetAddress().isLoopbackAddress()) {
                accepted.close();
                accepted = server.accept();
            }
            final Socket client = accepted;
            boolean erased = false;
            try (client; BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!erased && line.trim().startsWith("ERASE:")) {
                        erased = true;
                        playSequence();
                        Minecraft.getInstance().execute(() -> PacketDistributor.sendToServer(new RubyNet.Erase()));
                    }
                }
            }
        } catch (IOException e) {
            LOG.info("[terminal] connection ended: {}", e.getMessage());
        }
    }
}