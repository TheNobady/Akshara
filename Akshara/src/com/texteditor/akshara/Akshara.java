package com.texteditor.akshara;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;

public class Akshara {

	public static final String APP_NAME = "Akshara";

	private static final int HOME = 1000;
	private static final int ARROW_UP = 1001;
	private static final int ARROW_DOWN = 1002;
	private static final int ARROW_RIGHT = 1003;
	private static final int ARROW_LEFT = 1004;
	private static final int PAGE_UP = 1005;
	private static final int PAGE_DOWN = 1006;
	private static final int END = 1007;
	private static final int DEL = 1008;

	public static LibC.Termios defaultAttributes;

	public static String VERSION = "v1";

	public static int rows = 10;
	public static int columns = 10;

	public static int cursorX = 0;
	public static int cursorY = 0;
	public static int offSetY = 0;
	public static int offSetX = 0;

	private static List<String> content = new ArrayList<>();

	public static void main(String[] args) throws IOException {

		openFile(args);
		enableRawMode();
		initEditor();

		while (true) {
			scroll();
			refreshScreen();
			int key = readKey();
			handleKey(key);

		}
	}

	private static void scroll() {
		if (cursorY >= rows + offSetY) {
			offSetY = cursorY - rows + 1;
		} else if (cursorY < offSetY) {
			offSetY = cursorY;
		}

		if (cursorX >= columns + offSetX) {
			offSetX = cursorX - columns + 1;
		} else if (cursorX < offSetX) {
			offSetX = cursorX;
		}
	}

	private static void openFile(String[] args) {
		if (args.length == 1) {
			File f = new File(args[0]);
			if (f.exists()) {
				try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
					String line;
					while ((line = in.readLine()) != null) {
						content.add(line);
					}
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		}

	}

	private static void initEditor() {
		LibC.Winsize winsize = getWindowSize();
		columns = winsize.ws_col;
		rows = winsize.ws_row - 1;
	}

	private static void refreshScreen() {

		StringBuilder builder = new StringBuilder();

		// We use ANSI escape codes to manipulate the screen

		// builder.append("\033[2J");
		builder.append("\033[H");

		drawContent(builder);

		drawStatusMessage(builder);

		drawCursor(builder);

		System.out.print(builder);
	}

	private static void drawCursor(StringBuilder builder) {
		builder.append(String.format("\033[%d;%dH", cursorY - offSetY + 1, cursorX - offSetX + 1));

	}

	private static void drawStatusMessage(StringBuilder builder) {
		String statusbar = APP_NAME + " - " + VERSION;
		builder.append("\033[7m").append(APP_NAME).append(" - ").append(VERSION)
				.append(" ".repeat(Math.max(0, columns - statusbar.length()))).append("\033[0m");

	}

	private static void drawContent(StringBuilder builder) {
		for (int i = 0; i < rows; i++) {
			int fileI = offSetY + i;
			if (fileI >= content.size()) {
				builder.append("~");
			} else {
				String line = content.get(fileI);

				int lengthToDraw = line.length() - offSetX;

				if (lengthToDraw < 0) {
					lengthToDraw = 0;
				}
				if (lengthToDraw > columns) {
					lengthToDraw = columns;
				}
				if (lengthToDraw > 0) {
					builder.append(line, offSetX, offSetX + lengthToDraw);
				}

			}
			builder.append("\033[K\r\n");
		}

	}

	private static void handleKey(int key) {

		// if the key pressed is q exit Akshara
		if (key == 'q') {
			exit();
		} else if (List.of(ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT, HOME, END, PAGE_UP, PAGE_DOWN)
				.contains(key)) {
			moveCursor(key);
		}

//		else {
//			System.out.print(((char) key) + " -> " + key + "\r\n");
//		}

	}

	private static void exit() {
		// cleaning the screen
		System.out.print("\033[2J");
		System.out.print("\033[H");
		// Very important switching back to cooked mode
		LibC.INSTANCE.tcsetattr(LibC.SYSTEM_IN_FD, LibC.TCSAFLUSH, defaultAttributes);
		System.exit(0);

	}

	private static void moveCursor(int key) {
		String line = currentLine();
		switch (key) {
		case ARROW_UP -> {
			if (cursorY > 0) {
				cursorY--;
			}
		}

		case ARROW_DOWN -> {
			if (cursorY < content.size())
				cursorY++;
		}

		case ARROW_LEFT -> {
			if (cursorX > 0) {
				cursorX--;
			} else if (cursorX == 0 && cursorY >= 1) {
//				cursorY--;
//				cursorX = previousLine().length();
				moveCursor(ARROW_UP);
				cursorX = currentLine().length();
			}
		}

		case ARROW_RIGHT -> {
			if (line != null && cursorX < line.length()) {
				cursorX++;
			} else if (line != null && cursorX == line.length()) {
				moveCursor(ARROW_DOWN);
				cursorX = 0;
			}
		}
		case PAGE_UP, PAGE_DOWN -> {

			if (key == PAGE_UP) {

				cursorY = offSetY;

			} else if (key == PAGE_DOWN) {

				cursorY = offSetY + rows - 1;

				if (cursorY > content.size()) {

					cursorY = content.size();
				}
			}

			for (int i = 0; i < rows; i++) {
				moveCursor(key == PAGE_UP ? ARROW_UP : ARROW_DOWN);
			}
		}

		case HOME -> cursorX = 0;
		case END -> {
			if (line != null) {
				cursorX = line.length();
			}
		}
		}
		String newLine = currentLine();
		if (newLine != null && cursorX > newLine.length()) {
			cursorX = newLine.length();
		}
	}

//	private static String previousLine() {
//		// TODO Auto-generated method stub
//		return cursorY >= 1 ? content.get(--cursorY) : null;
//	}

	private static String currentLine() {
		return cursorY < content.size() ? content.get(cursorY) : null;
	}

	private static int readKey() throws IOException {
		int key = System.in.read();
		// check if the input is a escape sequence
		if (key != '\033') {
			return key;
		}

		int nextKey = System.in.read();
		if (nextKey != '[' && nextKey != 'O') {
			return nextKey;
		}

		// catching arrow keys, home , end and page up down etc...
		if (nextKey == '[') {
			int anotherKey = System.in.read();
			return switch (anotherKey) {
			case 'A' -> ARROW_UP;
			case 'B' -> ARROW_DOWN;
			case 'C' -> ARROW_RIGHT;
			case 'D' -> ARROW_LEFT;
			case 'H' -> HOME;
			case 'F' -> END;
			case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> { // e.g: esc[5~ == page_up
				int andAnotherKey = System.in.read();
				if (andAnotherKey != '~') {
					yield andAnotherKey;
				}

				switch (anotherKey) {
				case '1':
				case '7':
					yield HOME;
				case '3':
					yield DEL;
				case '4':
				case '8':
					yield END;
				case '5':
					yield PAGE_UP;
				case '6':
					yield PAGE_DOWN;
				default:
					yield andAnotherKey;
				}
			}
			default -> anotherKey;
			};
		}

		else {
			return switch (nextKey) {
			case 'H' -> HOME;
			case 'F' -> END;
			default -> nextKey;
			};
		}
	}

	private static void enableRawMode() {
		// Getting the current attributes of the system
		LibC.Termios termios = new LibC.Termios();
		int returnCode = LibC.INSTANCE.tcgetattr(LibC.SYSTEM_IN_FD, termios);

		// Saving the copy of the current attributes of the terminal to restore cooked
		// mode
		defaultAttributes = LibC.Termios.of(termios);

		if (returnCode != 0) {
			// internal error
			System.err.println("Some error occured ");
			System.exit(0);
		}

		// With the help of bitwise operation we negate the values, such has turn off
		// echoing,
		// canonical,implementation-defined input processing and signals
		termios.c_lflag &= ~(LibC.ECHO | LibC.ICANON | LibC.IEXTEN | LibC.ISIG);
		// Disable flow and stop the translations
		termios.c_iflag &= ~(LibC.IXON | LibC.ICRNL);
		// disable output post-processing
		termios.c_oflag &= ~(LibC.OPOST);

//These to control the read(), not useful for this application
//		termios.c_cc[LibC.VMIN] = 0;
//		termios.c_cc[LibC.VTIME] = 1;

		returnCode = LibC.INSTANCE.tcsetattr(LibC.SYSTEM_IN_FD, LibC.TCSAFLUSH, termios);

	}

	private static LibC.Winsize getWindowSize() {
		final LibC.Winsize winsize = new LibC.Winsize();
		final int returnCode = LibC.INSTANCE.ioctl(LibC.SYSTEM_IN_FD, LibC.TIOCGWINSZ, winsize);

		if (returnCode != 0) {
			System.err.println("ioctl failed");
			System.exit(0);
		}

		return winsize;

	}
}

/*
 * This interface will help use make direct Linux syscalls i.e., provide an
 * interface between a process and the operating system
 */
interface LibC extends Library {

	// This is a constant that represents the default data stream input (stdin),
	// here the kernel sees 0
	// Terminal configurations need a file descriptor that points to the terminal
	// device
	int SYSTEM_IN_FD = 0;

	// These are octal bitmask values from Linux kernel headers,
	// meaning these are just octal values that represent these in the terminal
	// setting

	/* from Linux kernel asm/termbits.h */
	/*
	 * #define ISIG 0000001 -> octal #define ICANON 0000002 #define ECHO 0000010
	 * #define IEXTEN 0100000 #define IXON 0002000 #define ICRNL 0000400 #define
	 * OPOST 0000001
	 */

	int ISIG = 1, ICANON = 2, ECHO = 10, TCSAFLUSH = 2, IXON = 2000, ICRNL = 400, IEXTEN = 100000, OPOST = 1, VMIN = 6,
			VTIME = 5;

	// Stands for Terminal IO Control Get WINdow SiZe is a ioctl request code we
	// need
	// to pass on the ioctl to get the window size
	int TIOCGWINSZ = 0x5413;

	/*
	 * This creates a dynamic proxy object and every method call on INSTANCE becomes
	 * a real native syscall under the hood
	 */
	LibC INSTANCE = Native.load("c", LibC.class);

	// This mirrors the termios struct in memory so we can pass it to the sys calls
	// @Structure.FieldOrder tell jna the memory layout since in C every thing is
	// one block of memory

	@Structure.FieldOrder(value = { "c_iflag", "c_oflag", "c_cflag", "c_lflag", "c_cc" })
	class Termios extends Structure {
		// copied from the man page as it is
		public int c_iflag; /* input modes */
		public int c_oflag; /* output modes */
		public int c_cflag; /* control modes */
		public int c_lflag; /* local modes */
		public byte[] c_cc = new byte[19]; /* special characters */

		public static Termios of(Termios t) {
			Termios clone = new Termios();
			clone.c_iflag = t.c_iflag;
			clone.c_oflag = t.c_oflag;
			clone.c_cflag = t.c_cflag;
			clone.c_lflag = t.c_lflag;
			clone.c_cc = t.c_cc.clone();
			return clone;
		}

		@Override
		public String toString() {
			return "Termios [c_iflag=" + c_iflag + ", c_oflag=" + c_oflag + ", c_cflag=" + c_cflag + ", c_lflag="
					+ c_lflag + ", c_cc=" + Arrays.toString(c_cc) + "]";
		}
	}

	// Mirrors the winsize struct in <sys/ioctl.h> to get the window size

	@Structure.FieldOrder(value = { "ws_row", "ws_col", "ws_xpixel", "ws_ypixel" })
	public class Winsize extends Structure {
		public short ws_row, ws_col, ws_xpixel, ws_ypixel;
	}

	// Native methods

	// get the default terminal attributes
	int tcgetattr(int fd, Termios termios);

	// set the terminal attributes
	int tcsetattr(int fd, int optional_actions, Termios termios);

	// input and output control is the system call under Device management, device
	// specific operations
	int ioctl(int fd, int opt, Winsize winze);

}