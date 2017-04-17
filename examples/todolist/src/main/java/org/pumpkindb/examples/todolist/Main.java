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
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import io.netty.buffer.ByteBuf;
import org.pumpkindb.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
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
                    new Instruction("HLC"),
                    new Instruction("CONCAT"),
                    new Instruction("SWAP"),
                    new Instruction("ASSOC")
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
                    new Data(PREFIX),
                    // uuid value prefix
                    new Instruction("ROT"),
                    // value prefix uuid
                    new Instruction("HLC"),
                    new Instruction("CONCAT"),
                    new Instruction("CONCAT"),
                    // value key
                    new Instruction("SWAP"),
                    new Instruction("ASSOC")
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

        private final UUID uuid = UUID.randomUUID();
        private final Consumer<List<Encodable>> callback;

        Handler(Consumer<List<Encodable>> callback) {this.callback = callback;}

        public UUID getUuid() {
            return uuid;
        }

        @Override public void accept(ByteBuf byteBuf) {
            List<Encodable> encodables = new ArrayList<>();
            new BinaryParsingIterator(byteBuf).forEachRemaining(encodables::add);
            callback.accept(encodables);
        }

    }

    private static Encodable fetchTodoList(Handler handler) {
        return new Encodables(Arrays.asList(
                new Uuid(handler.getUuid()),
                new Instruction("SUBSCRIBE"),
                new Closure(
                        new Utf8String("AddedTodoItem"),
                        new Closure(
                                new Instructions("CURSOR/VAL DUP DUP CURSOR DUP ROT"),
                                new Utf8String("ChangedTodoItem"),
                                new Instructions("SWAP CONCAT CURSOR/SEEKLAST DROP CURSOR/VAL"),
                                new Instructions("SWAP"),
                                new Utf8String("ChangedTodoStatus"),
                                new Instructions("SWAP CONCAT CURSOR DUP ROT CURSOR/SEEKLAST"),
                                new Closure(new Instructions("CURSOR/VAL")),
                                new Closure(new Instruction("DROP"), new Bool(false)),
                                new Instructions("IFELSE"),
                                new UnsignedInteger(3),
                                new Instruction("WRAP"),
                                new Uuid(handler.getUuid()),
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
                        new Instruction("SWAP"),
                        new Utf8String("ChangedTodoStatus"),
                        new Instructions("SWAP HLC CONCAT CONCAT"),
                        new Instructions("SWAP ASSOC COMMIT")
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
        BasicWindow window = new BasicWindow("TODO [up/down to navigate, space/enter — toggle, n — new item, q — " +
                                                     "quit]");
        window.setHints(Arrays.asList(Window.Hint.CENTERED, Window.Hint.EXPANDED));

        CheckBoxList<String> checkBoxList = new CheckBoxList<>();
        window.setComponent(checkBoxList);

        List<UUID> itemMapping = new ArrayList<>();

        Handler handler = new Handler(encodables -> {

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
        Client client = new Client("localhost", handler);
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
            client.send(fetchTodoList(handler));
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
                        client.send(fetchTodoList(handler));
                    }
                }
                if (keyStroke.getCharacter() == Character.valueOf('q')) {
                    window.close();
                }
            }
        });
//        client.send(new StackCollectingProgram(new Closure(fetchTodoList(handler))));
        client.send(fetchTodoList(handler));

        gui.addWindowAndWait(window);

        client.shutdown();

        screen.stopScreen();
    }
}
