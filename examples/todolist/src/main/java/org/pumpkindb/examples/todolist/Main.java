/**
 * Copyright (c) 2017, All Contributors (see CONTRIBUTORS file)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.pumpkindb.examples.todolist;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialog;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.pumpkindb.*;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class Main {


    static class StackCollectingProgram implements Encodable {
        private final Encodable program;

        StackCollectingProgram(Encodable program) {this.program = program;}


        @Override public void encode(ByteBuf buffer) {
            UUID uuid = UUID.randomUUID();
            Encodables script = new Encodables(Arrays.asList(
                    program,
                    new Instruction("TRY"),
                    new Instruction("STACK"),
                    new Uuid(uuid),
                    new Instruction("SUBSCRIBE"),
                    new Instruction("SWAP"),
                    new Uuid(uuid),
                    new Instruction("PUBLISH"),
                    new Instruction("UNSUBSCRIBE")
            ));
            script.encode(buffer);
        }
    }

    static class Item  {
        private final String value;
        private final UUID uuid = UUID.randomUUID();

        Item(String value) {this.value = value;}

        public String getValue() {
            return value;
        }

        public UUID getUuid() {
            return uuid;
        }

    }

    // expects UUID on the stack
    static class AddTodoItem implements Encodable {
        public static final byte[] PREFIX = "AddedTodoItem".getBytes();

        @Override public void encode(ByteBuf buffer) {
            Encodables script = new Encodables(Arrays.asList(
                    new Data(PREFIX),
                    new Instructions("HLC CONCAT 2DUP SWAP ASSOC HLC ROT SWAP CONCAT SWAP ASSOC")
            ));
            script.encode(buffer);
        }
    }



    // top: value
    // 2nd: uuid
    static class ChangeTodoItem implements Encodable {
        public static final byte[] PREFIX = "ChangedTodoItem".getBytes();

        @Override public void encode(ByteBuf buffer) {
            Encodables script = new Encodables(Arrays.asList(
                    new Instruction("OVER"),
                    new Data(PREFIX),
                    new Instructions("SWAP HLC CONCAT CONCAT DUP -ROT SWAP ASSOC SWAP HLC CONCAT SWAP ASSOC")
            ));
            script.encode(buffer);
        }
    }

    // uuid value
    static class AddNewTodoItem implements Encodable {
        @Override public void encode(ByteBuf buffer) {
            // uuid value
            new Instruction("OVER").encode(buffer);
            // uuid value uuid
            new AddTodoItem().encode(buffer);
            // uuid value
            new ChangeTodoItem().encode(buffer);
        }
    }

    static class DefineInstruction implements Encodable {
        private final Encodable encodable;
        private final String name;

        DefineInstruction(Encodable encodable, String name) {
            this.encodable = encodable;
            this.name = name;
        }

        @Override public void encode(ByteBuf buffer) {
            encodable.encode(buffer);
            new Closure(new Instruction(name)).encode(buffer);
            new Instruction("DEF").encode(buffer);
        }
    }

    static class Handler implements MessageHandler {

        private final Consumer<List<Encodable>> callback;

        Handler(Consumer<List<Encodable>> callback) {this.callback = callback;}

        @Override public void accept(ByteBuf byteBuf) {
            List<Encodable> encodables = new ArrayList<>();
            new BinaryParsingIterator(byteBuf).forEachRemaining(encodables::add);
            callback.accept(encodables);
        }

    }


    static class RoutingHandler implements MessageHandler {

        private final UUID uuid = UUID.randomUUID();
        private final Map<String, Handler> handlers = new HashMap<>();

        public UUID getUuid() {
            return uuid;
        }

        public void registerHandler(String key, Handler handler) {
            handlers.put(key, handler);
        }

        public void unregisterHandler(String key) {
            handlers.remove(key);
        }

        @Override public void accept(ByteBuf byteBuf) {
            BinaryParsingIterator iterator = new BinaryParsingIterator(byteBuf);
            if (iterator.hasNext()) {
                Data key = (Data) iterator.next();
                Handler handler = handlers.get(new String(key.getData()));
                if (handler != null) {
                    handler.accept(byteBuf);
                }
            }
        }

    }


    private static Encodable fetchTodoList(UUID uuid) {
        return new Encodables(Arrays.asList(
                new Uuid(uuid),
                new Instruction("SUBSCRIBE"),
                new Closure(
                        new Utf8String("AddedTodoItem"),
                        new Closure(
                                new Utf8String("Items"), // routing key
                                new Instructions("SWAP CURSOR/VAL DUP DUP CURSOR DUP ROT"),
                                new Utf8String("ChangedTodoItem"),
                                new Instructions("SWAP CONCAT CURSOR/SEEKLAST DROP CURSOR/VAL"),
                                new Instructions("SWAP"),
                                new Utf8String("ChangedTodoStatus"),
                                new Instructions("SWAP CONCAT CURSOR DUP ROT CURSOR/SEEKLAST"),
                                new Closure(new Instructions("CURSOR/VAL")),
                                new Closure(new Instruction("DROP"), new Bool(false)),
                                new Instructions("IFELSE"),
                                new UnsignedInteger(4),
                                new Instruction("WRAP"),
                                new Uuid(uuid),
                                new Instruction("PUBLISH"),
                                new Bool(true)
                        ),
                        new Instruction("CURSOR/DOWHILE-PREFIXED")
                ),
                new Instruction("READ"),
                new Instruction("UNSUBSCRIBE")
        ));
    }

    private static Encodable eventLog(UUID itemUuid, UUID routingUuid) {
        return new Encodables(Arrays.asList(
                new Uuid(routingUuid),
                new Instruction("SUBSCRIBE"),
                new Closure(
                        new Uuid(itemUuid),
                        new Closure(
                                new Utf8String("EventLog"), // routing key
                                new Instructions("SWAP DUP CURSOR/KEY SWAP CURSOR/VAL DUP RETR"),
                                new UnsignedInteger(4),
                                new Instruction("WRAP"),
                                new Uuid(routingUuid),
                                new Instruction("PUBLISH"),
                                new Bool(true)
                        ),
                        new Instruction("CURSOR/DOWHILE-PREFIXED")
                ),
                new Instruction("READ"),
                new Instruction("UNSUBSCRIBE")
        ));
    }

    private static DefineInstruction todoMarkDefinition() {
        return new DefineInstruction(new Closure(
                new Closure(
                        // UUID t/f
                        new Instruction("OVER"),
                        new Utf8String("ChangedTodoStatus"),
                        new Instructions("SWAP HLC CONCAT CONCAT DUP -ROT SWAP ASSOC SWAP HLC CONCAT SWAP ASSOC"),
                        new Instruction("COMMIT")
                ),
                new Instructions("WRITE")
        ), "TODO/MARK");
    }

    private static DefineInstruction todoNewDefinition() {
        return new DefineInstruction(new Closure(
                new Closure(
                        new AddNewTodoItem(),
                        new Instruction("COMMIT")
                ),
                new Instruction("WRITE")
        ), "TODO/NEW");
    }

    private static boolean hasPrefix(byte[] b, String prefix) {
        ByteBuf bb = Unpooled.wrappedBuffer(b);
        return (bb.readableBytes() > prefix.length()) &&
               (bb.readCharSequence(prefix.length(), Charset.defaultCharset()).toString()
                        .contentEquals(prefix));

    }

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {

        Terminal terminal = new DefaultTerminalFactory().createTerminal();
        TerminalScreen screen = new TerminalScreen(terminal);

        screen.startScreen();

        // Create gui and start gui
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen, new DefaultWindowManager(), new EmptySpace(TextColor.ANSI.BLUE));

        MessageDialog messageDialog = new MessageDialogBuilder()
                .setTitle("Welcome to PumpkinDB TodoList")
                .setText("This example shows basic PumpkinDB functionality\nby storing all todo list management " +
                                 "events in it\nand querying the state with PumpkinScript.\n\nEnjoy!")
                .build();

        messageDialog.showDialog(gui);



        // Create window to hold the panel
        BasicWindow window = new BasicWindow("TODO");
        window.setHints(Arrays.asList(Window.Hint.CENTERED, Window.Hint.EXPANDED));

        CheckBoxList<String> checkBoxList = new CheckBoxList<>();
        Label help = new Label("[up/down] navigate [space/enter] toggle [n] new item [l] event log\n" +
                                     "[q] quit");
        Panel helpPanel = new Panel();
        helpPanel.addComponent(help);

        Panel panel = new Panel(new BorderLayout());
        panel.addComponent(checkBoxList, BorderLayout.Location.CENTER);
        panel.addComponent(helpPanel.withBorder(Borders.singleLine("Help")), BorderLayout.Location.BOTTOM);

        window.setComponent(panel);
        window.setFocusedInteractable(checkBoxList);

        List<UUID> itemMapping = new ArrayList<>();

        Handler itemListHandler = new Handler(encodables -> {

            ArrayList<Encodable> e = new ArrayList<>(encodables);

            while (!e.isEmpty()) {
                Data id = (Data) e.remove(0);
                Data text = (Data) e.remove(0);
                Data status = (Data) e.remove(0);
                boolean checked = status.getData()[0] == 1;
                checkBoxList.addItem(new String(text.getData()), checked);

                ByteBuffer bb = ByteBuffer.wrap(id.getData());
                long ms = bb.getLong();
                long ls = bb.getLong();
                itemMapping.add(new UUID(ms, ls));
            }

        });

        RoutingHandler routingHandler = new RoutingHandler();
        routingHandler.registerHandler("Items", itemListHandler);

        Client client = new Client("localhost", routingHandler);
        client.connect();

        checkBoxList.addListener((itemIndex, checked) -> {
            Encodables cmd = new Encodables(Arrays.asList(
                    todoMarkDefinition(),
                    new Uuid(itemMapping.get(itemIndex)),
                    new Bool(checked),
                    new Instruction("TODO/MARK")));
            client.send(cmd);
            itemMapping.clear();
            checkBoxList.clearItems();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            client.send(fetchTodoList(routingHandler.getUuid()));
        });

        window.addWindowListener(new WindowListenerAdapter() {
            @Override public void onUnhandledInput(Window basePane, KeyStroke keyStroke, AtomicBoolean hasBeenHandled) {
                if (keyStroke.getCharacter() == Character.valueOf('n')) {
                    String item = TextInputDialog.showDialog(gui, "New item", "Enter your new todo list item", "");
                    if (item != null) {
                        Item i = new Item(item);
                        Encodables cmd = new Encodables(Arrays.asList(
                                todoNewDefinition(),
                                new Uuid(i.getUuid()), new Utf8String(i.getValue()),
                                new Instruction("TODO/NEW")));
                        itemMapping.clear();
                        checkBoxList.clearItems();
                        client.send(cmd);
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        client.send(fetchTodoList(routingHandler.getUuid()));
                    }
                }
                if (keyStroke.getCharacter() == Character.valueOf('q')) {
                    window.close();
                }
                if (keyStroke.getCharacter() == Character.valueOf('l')) {
                    BasicWindow window = new BasicWindow("Event log");
                    window.setHints(Arrays.asList(Window.Hint.CENTERED, Window.Hint.EXPANDED));

                    Panel panel = new Panel(new BorderLayout());
                    Table<String> table = new Table<>("Timestamp", "What happened");

                    routingHandler.registerHandler("EventLog", new Handler(encodables -> {
                        Data key = (Data) encodables.remove(0);
                        Data reference = (Data) encodables.remove(0);
                        Data value = (Data) encodables.remove(0);

                        // Parse key
                        ByteBuf bb = Unpooled.wrappedBuffer(key.getData());
                        bb.skipBytes(16); // uuid
                        bb.skipBytes(4); // HLC epoch
                        byte[] ts = new byte[8];
                        bb.readBytes(ts); // timestamp

                        long ms = TimeUnit.MILLISECONDS
                                .convert(new BigInteger(ts).longValue(), TimeUnit.NANOSECONDS);
                        Date date = new Date(ms);

                        // Parse reference
                        String event = "Unknown";

                        if (hasPrefix(reference.getData(), "AddedTodoItem")) {
                            event = "Item added";
                        }

                        if (hasPrefix(reference.getData(), "ChangedTodoItem")) {
                            event = "Item changed to: " + new String(value.getData());
                        }

                        if (hasPrefix(reference.getData(), "ChangedTodoStatus")) {
                            boolean done = value.getData()[0] == 1;
                            event = "Item marked as " + (done ? "done" : "not done");
                        }


                        String formattedDate = new SimpleDateFormat("EEE MMM d yyyy h:mm a", Locale.ENGLISH)
                                .format(date);

                        table.getTableModel().addRow(formattedDate, event);
                    }));

                    client.send(eventLog(itemMapping.get(checkBoxList.getSelectedIndex()), routingHandler.getUuid()));

                    panel.addComponent(table, BorderLayout.Location.CENTER);
                    panel.addComponent(new Button("Close", window::close), BorderLayout.Location.BOTTOM);

                    window.setComponent(panel);
                    gui.addWindowAndWait(window);
                }
            }
        });

//        client.send(new StackCollectingProgram(new Closure(fetchTodoList(itemListHandler))));
        client.send(fetchTodoList(routingHandler.getUuid()));

        gui.addWindowAndWait(window);

        client.shutdown();

        screen.stopScreen();
    }
}
