/*
 * PlayerInfo RCON dev tool
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Minimal Source RCON client for sending console commands to the dev server.
 * Run directly with the JDK (no compile step):
 *   java tools/rcon.java <host> <port> <password> <command...>
 * Example:
 *   java tools/rcon.java 127.0.0.1 25575 devpass123 op Steve
 */
public class rcon {

    private static final int TYPE_RESPONSE = 0;
    private static final int TYPE_COMMAND = 2;
    private static final int TYPE_AUTH = 3;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: java tools/rcon.java <host> <port> <password> <command...>");
            System.exit(64);
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String password = args[2];
        String command = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(10000);
            OutputStream out = socket.getOutputStream();
            InputStream in = new BufferedInputStream(socket.getInputStream());

            send(out, 1, TYPE_AUTH, password);
            // The server replies to auth with a single SERVERDATA_AUTH_RESPONSE
            // packet, whose numeric type (2) equals TYPE_COMMAND.
            byte[] auth = await(in, TYPE_COMMAND);
            if (intAt(auth, 0) == -1) {
                System.err.println("RCON authentication failed");
                System.exit(2);
            }

            send(out, 2, TYPE_COMMAND, command);
            try {
                byte[] response = await(in, TYPE_RESPONSE, TYPE_COMMAND);
                String payload = new String(response, 8, response.length - 10, StandardCharsets.UTF_8);
                System.out.print(payload.isEmpty() ? "(no output)" : payload);
                if (!payload.isEmpty() && !payload.endsWith("\n")) {
                    System.out.println();
                }
            } catch (IOException noReply) {
                // e.g. "stop" closes the connection without responding
                System.out.println("(no output)");
            }
        }
    }

    /** Reads packets, skipping any whose type is not one of the expected ones. */
    private static byte[] await(InputStream in, int... types) throws IOException {
        while (true) {
            byte[] packet = read(in);
            int type = intAt(packet, 4);
            for (int expected : types) {
                if (type == expected) {
                    return packet;
                }
            }
        }
    }

    private static int intAt(byte[] data, int offset) {
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(offset);
    }

    private static void send(OutputStream out, int id, int type, String payload) throws IOException {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        int length = 4 + 4 + body.length + 2;
        ByteBuffer packet = ByteBuffer.allocate(4 + length).order(ByteOrder.LITTLE_ENDIAN);
        packet.putInt(length).putInt(id).putInt(type).put(body).put((byte) 0).put((byte) 0);
        out.write(packet.array());
        out.flush();
    }

    private static byte[] read(InputStream in) throws IOException {
        byte[] lengthBuf = in.readNBytes(4);
        if (lengthBuf.length < 4) {
            throw new IOException("connection closed by server");
        }
        int length = ByteBuffer.wrap(lengthBuf).order(ByteOrder.LITTLE_ENDIAN).getInt();
        byte[] data = in.readNBytes(length);
        if (data.length < length) {
            throw new IOException("truncated RCON response");
        }
        return data;
    }
}
