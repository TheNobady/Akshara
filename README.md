# Akshara

Akshara is a small text editor that runs in the terminal. I made it in Java mostly to learn how terminal editors like vim/nano actually work under the hood (raw mode, escape codes, etc).

It uses [JNA](https://github.com/java-native-access/jna) to call the native C functions (`tcgetattr`, `tcsetattr`, `ioctl`) so the terminal can be put into raw mode. On Windows it uses the Kernel32 console functions instead.

## Features

- Open a file and edit it
- Save with `Ctrl + S` (asks for a filename if you didn't open one)
- Search with `Ctrl + F`
- Move around with arrow keys, Home, End, Page Up and Page Down
- Scrolls up/down and left/right when the text doesn't fit on screen
- Backspace / Delete and Enter work like you'd expect
- Status bar at the bottom
- Works on Linux, Mac and Windows

## Requirements

- Java 21 (it needs at least Java 16 because I used records and switch expressions)
- The JNA jar, it's already in the `lib` folder

## How to run

First go into the project folder:

```
cd Akshara
```

Compile it:

```
javac -cp lib/jna-5.18.1.jar -d bin src/com/texteditor/akshara/Akshara.java
```

Then run it:

```
java -cp "bin:lib/jna-5.18.1.jar" com.texteditor.akshara.Akshara
```

To open a file just pass the file name at the end:

```
java -cp "bin:lib/jna-5.18.1.jar" com.texteditor.akshara.Akshara myfile.txt
```

On Windows use `;` instead of `:` in the classpath:

```
java -cp "bin;lib/jna-5.18.1.jar" com.texteditor.akshara.Akshara myfile.txt
```

You can also just import it into Eclipse and run `Akshara.java` from there (but it needs a real terminal, the Eclipse console won't work properly).

## Controls

| Key | What it does |
|-----|--------------|
| `Ctrl + Q` | Quit |
| `Ctrl + S` | Save |
| `Ctrl + F` | Search (arrow keys go to next/previous match, Enter or Esc to stop) |
| Arrow keys | Move the cursor |
| `Home` / `End` | Go to start / end of the line |
| `Page Up` / `Page Down` | Scroll a full screen up or down |

## Known issues / TODO

- No undo yet
- Quitting doesn't warn you if you have unsaved changes, so save first!
- Tabs and syntax highlighting are not handled
- Search spelling in the status bar is a bit off lol

## Project structure

```
Akshara/
├── lib/jna-5.18.1.jar     # JNA library
├── src/com/texteditor/akshara/
│   └── Akshara.java       # the whole editor is in here
└── TEST                   # random text file I use for testing
```
