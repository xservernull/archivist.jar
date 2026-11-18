package com.xservernull.archivist.client;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.events.AdminPanelNet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The admin panel, the green terminal window of the old mod
 */
public final class AdminPanelClient {
    private AdminPanelClient() {}

    private static final Logger LOG = LogUtils.getLogger();
    private static final String JAR = "/assets/archivist/binary/AdminPanel.jar";
    private static final String GO = "GO:";
    private static final String PANEL = "archivist.panel.";
    private static final int CONNECT_TIMEOUT_MS = 60_000;

    private static Process process;
    private static boolean hookAdded;

    public static synchronized void open(AdminPanelNet.Open data) {
        if (process != null && process.isAlive()) return;
        List<String> lines = lines(data);
        ServerSocket socket = null;
        try {
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), Archivist.MOD_ID);
            Files.createDirectories(dir);
            Path jar = dir.resolve("adminpanel.jar");
            try (InputStream in = AdminPanelClient.class.getResourceAsStream(JAR)) {
                if (in == null) throw new FileNotFoundException(JAR);
                byte[] bytes = in.readAllBytes();
                if (!Files.exists(jar) || !Arrays.equals(Files.readAllBytes(jar), bytes)) Files.write(jar, bytes);
            }
            String java = ProcessHandle.current().info().command()
                    .orElseThrow(() -> new IOException("could not find the java executable"));
            socket = new ServerSocket(0);
            ProcessBuilder builder = new ProcessBuilder(java, "-Djava.awt.headless=false", "-jar",
                    jar.toAbsolutePath().toString(), Integer.toString(socket.getLocalPort()));
            builder.redirectErrorStream(true).redirectOutput(dir.resolve("adminpanel.log").toFile());
            process = builder.start();
            if (!hookAdded) {
                hookAdded = true;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    Process p = process;
                    if (p != null) p.destroy();
                }));
            }
            ServerSocket listening = socket;
            Thread thread = new Thread(() -> talk(listening, lines), "archivist-adminpanel");
            thread.setDaemon(true);
            thread.start();
            LOG.info("[panel] window started (pid {})", process.pid());
        } catch (IOException | RuntimeException e) {
            LOG.error("[panel] could not open the window: {}", e.toString());
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
            process = null;
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal("[panel] could not open the window, see the log"), false);
            }
        }
    }

    private static List<String> lines(AdminPanelNet.Open data) {
        List<String> lines = new ArrayList<>();
        Lang.entries(PANEL).forEach((key, text) -> {
            if (!key.startsWith(PANEL + "log.") && !key.startsWith(PANEL + "hint.")) lines.add("T:" + key + "=" + oneLine(text));
        });
        data.dimensions().forEach(id -> lines.add("D:" + id));
        data.open().forEach(id -> lines.add("V:" + id));
        data.logs().forEach(text -> lines.add("LOG:" + oneLine(Lang.get(text))));
        data.hints().forEach(text -> lines.add("HINT:" + oneLine(Lang.get(text))));
        lines.add("READY");
        return lines;
    }


    private static String oneLine(String text) {
        return text.replace('\r', ' ').replace('\n', ' ');
    }
    private static void talk(ServerSocket server, List<String> lines) {
        try (server) {
            server.setSoTimeout(CONNECT_TIMEOUT_MS);
            Socket accepted = server.accept();
            while (!accepted.getInetAddress().isLoopbackAddress()) {
                accepted.close();
                accepted = server.accept();
            }
            try (Socket client = accepted;
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
                lines.forEach(out::println);
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith(GO)) {
                        String id = line.substring(GO.length()).trim();
                        Minecraft.getInstance().execute(() -> {
                            if (Minecraft.getInstance().player != null) PacketDistributor.sendToServer(new AdminPanelNet.Go(id));
                        });
                    }
                }
            }
        } catch (IOException e) {
            LOG.info("[panel] connection ended: {}", e.getMessage());
        }
    }
}