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
import java.util.function.BiConsumer;

import com.sun.jna.LastErrorException;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

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

	private static final int BACKSPACE = 127;

	public static String VERSION = "v1";

	public static int rows = 10;
	public static int columns = 10;

	public static int cursorX = 0;
	public static int cursorY = 0;
	public static int offSetY = 0;
	public static int offSetX = 0;

	private static Terminal terminal = Platform.isWindows() ? new WindowsTerminal()
			: Platform.isMac() ? new MacOsTerminal() : new UnixTerminal();

	private static List<String> content = new ArrayList<>();

	public static void main(String[] args) throws IOException {

		openFile(args);
		initEditor();

		while (true) {

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
		terminal.enableRawMode();
		WindowSize winsize = terminal.getWindowSize();
		columns = winsize.columns();
		rows = winsize.rows() - 1;
	}

	private static void refreshScreen() {

		scroll();

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

	static String statusbar;

	private static void drawStatusMessage(StringBuilder builder) {
		String message = statusbar != null ? statusbar : APP_NAME + " - " + VERSION;
		builder.append("\033[7m").append(message).append(" ".repeat(Math.max(0, columns - message.length())))
				.append("\033[0m");

	}

	public static void setStatusbar(String statusbar) {
		Akshara.statusbar = statusbar;
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
		if (key == ctrl('q')) {
			exit();
		} else if (key == ctrl('f')) {
			editorFind();
		} else if (List.of(ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT, HOME, END, PAGE_UP, PAGE_DOWN)
				.contains(key)) {
			moveCursor(key);
		}

//		else {
//			System.out.print(((char) key) + " -> " + key + "\r\n");
//		}

	}

	enum SearchDirection {
		FORWARDS, BACKWORDS
	}

	static SearchDirection searchDirection = SearchDirection.FORWARDS;

	static int lastMatch = -1;

	private static void editorFind() {
		prompt("Serach %s (Use ESC/Arrows/Enter)", (query, lastKeyPress) -> {

			if (query == null || query.isBlank()) {
				searchDirection = SearchDirection.FORWARDS;
				lastMatch = -1;
				return;
			}

			if (lastKeyPress == ARROW_UP || lastKeyPress == ARROW_LEFT) {
				searchDirection = SearchDirection.BACKWORDS;
			} else if (lastKeyPress == ARROW_DOWN || lastKeyPress == ARROW_RIGHT) {
				searchDirection = SearchDirection.FORWARDS;
			} else {
				searchDirection = SearchDirection.FORWARDS;
				lastMatch = -1;
			}

			int currentIndex = lastMatch;
			for (int i = 0; i < content.size(); i++) {

				currentIndex += searchDirection == SearchDirection.FORWARDS ? 1 : -1;
				
				if(currentIndex == content.size()) {
					currentIndex = 0;
				}else if (currentIndex == -1) {
					currentIndex = content.size() - 1;
				}

				String currentLine = content.get(currentIndex);
				int match = currentLine.indexOf(query);
				if (match != -1) {
					lastMatch = currentIndex;
					cursorY = currentIndex;
					cursorX = match;
					offSetY = content.size();
					break;
				}
			}
		});

	}

	private static void prompt(String message, BiConsumer<String, Integer> consumer) {

		StringBuilder userInput = new StringBuilder();

		while (true) {
			try {
				setStatusbar(!userInput.isEmpty() ? userInput.toString() : message);
				refreshScreen();
				int key = readKey();

				if (key == '\033' || key == '\r') {
					setStatusbar(null);
					return;
				} else if (key == DEL || key == BACKSPACE || key == ctrl('h')) {

					if (!userInput.isEmpty()) {
						userInput.deleteCharAt(userInput.length() - 1);

					} 
					
				}else if (!Character.isISOControl(key) && key < 128) {
					userInput.append((char) key);
				}
				consumer.accept(userInput.toString(), key);

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

	}

	// Method to check if the input is bitwise And 'q' (ctrl + q)
	private static int ctrl(char key) {
		// Anding q with a bitwising it via hex value
		return key & 0x1f;
	}

	private static void exit() {
		// cleaning the screen
		System.out.print("\033[2J");
		System.out.print("\033[H");
		// Very important switching back to cooked mode
		terminal.disableRawMode();
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

}

interface Terminal {

	void enableRawMode();

	void disableRawMode();

	WindowSize getWindowSize();
}

//A class for making native function in Mac OS only 
//encapsulate the terminal process for Mac system 
class MacOsTerminal implements Terminal {

	public static LibC.Termios defaultAttributes;

	public void enableRawMode() {
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

	public void disableRawMode() {
		LibC.INSTANCE.tcsetattr(LibC.SYSTEM_IN_FD, LibC.TCSAFLUSH, defaultAttributes);

	}

	public WindowSize getWindowSize() {
		final LibC.Winsize winsize = new LibC.Winsize();
		final int returnCode = LibC.INSTANCE.ioctl(LibC.SYSTEM_IN_FD, LibC.TIOCGWINSZ, winsize);

		if (returnCode != 0) {
			System.err.println("ioctl failed with return code[={}]" + returnCode);
			System.exit(1);
		}

		return new WindowSize(winsize.ws_row, winsize.ws_col);

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

		int ISIG = 1, ICANON = 2, ECHO = 10, TCSAFLUSH = 2, IXON = 2000, ICRNL = 400, IEXTEN = 100000, OPOST = 1,
				VMIN = 6, VTIME = 5;

		// Stands for Terminal IO Control Get WINdow SiZe is a ioctl request code we
		// need
		// to pass on the ioctl to get the window size
		int TIOCGWINSZ = 0x40087468;

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
			public long c_iflag; /* input modes */
			public long c_oflag; /* output modes */
			public long c_cflag; /* control modes */
			public long c_lflag; /* local modes */
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
}

//encapsulate the terminal process for Mac system 
class UnixTerminal implements Terminal {

	public static LibC.Termios defaultAttributes;

	public void enableRawMode() {
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

	public void disableRawMode() {
		LibC.INSTANCE.tcsetattr(LibC.SYSTEM_IN_FD, LibC.TCSAFLUSH, defaultAttributes);

	}

	public WindowSize getWindowSize() {
		final LibC.Winsize winsize = new LibC.Winsize();
		final int returnCode = LibC.INSTANCE.ioctl(LibC.SYSTEM_IN_FD, LibC.TIOCGWINSZ, winsize);

		if (returnCode != 0) {
			System.err.println("ioctl failed with return code[={}]" + returnCode);
			System.exit(1);
		}

		return new WindowSize(winsize.ws_row, winsize.ws_col);

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

		int ISIG = 1, ICANON = 2, ECHO = 10, TCSAFLUSH = 2, IXON = 2000, ICRNL = 400, IEXTEN = 100000, OPOST = 1,
				VMIN = 6, VTIME = 5;

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
}

class WindowsTerminal implements Terminal {

	private IntByReference inMode;
	private IntByReference outMode;

	@Override
	public void enableRawMode() {
		Pointer inHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_INPUT_HANDLE);

		inMode = new IntByReference();
		Kernel32.INSTANCE.GetConsoleMode(inHandle, inMode);

		int inMode;
		inMode = this.inMode.getValue() & ~(Kernel32.ENABLE_ECHO_INPUT | Kernel32.ENABLE_LINE_INPUT
				| Kernel32.ENABLE_MOUSE_INPUT | Kernel32.ENABLE_WINDOW_INPUT | Kernel32.ENABLE_PROCESSED_INPUT);

		inMode |= Kernel32.ENABLE_VIRTUAL_TERMINAL_INPUT;

		Kernel32.INSTANCE.SetConsoleMode(inHandle, inMode);

		Pointer outHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
		outMode = new IntByReference();
		Kernel32.INSTANCE.GetConsoleMode(outHandle, outMode);

		int outMode = this.outMode.getValue();
		outMode |= Kernel32.ENABLE_VIRTUAL_TERMINAL_PROCESSING;
		outMode |= Kernel32.ENABLE_PROCESSED_OUTPUT;
		Kernel32.INSTANCE.SetConsoleMode(outHandle, outMode);

	}

	@Override
	public void disableRawMode() {
		Pointer inHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_INPUT_HANDLE);
		Kernel32.INSTANCE.SetConsoleMode(inHandle, inMode.getValue());

		Pointer outHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
		Kernel32.INSTANCE.SetConsoleMode(outHandle, outMode.getValue());
	}

	@Override
	public WindowSize getWindowSize() {
		final Kernel32.CONSOLE_SCREEN_BUFFER_INFO info = new Kernel32.CONSOLE_SCREEN_BUFFER_INFO();
		final Kernel32 instance = Kernel32.INSTANCE;
		final Pointer handle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
		instance.GetConsoleScreenBufferInfo(handle, info);
		return new WindowSize(info.windowHeight(), info.windowWidth());
	}

	interface Kernel32 extends StdCallLibrary {

		Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

		/**
		 * The CryptUIDlgSelectCertificateFromStore function displays a dialog box that
		 * allows the selection of a certificate from a specified store.
		 *
		 * @param hCertStore        Handle of the certificate store to be searched.
		 * @param hwnd              Handle of the window for the display. If NULL,
		 *                          defaults to the desktop window.
		 * @param pwszTitle         String used as the title of the dialog box. If NULL,
		 *                          the default title, "Select Certificate," is used.
		 * @param pwszDisplayString Text statement in the selection dialog box. If NULL,
		 *                          the default phrase, "Select a certificate you want
		 *                          to use," is used.
		 * @param dwDontUseColumn   Flags that can be combined to exclude columns of the
		 *                          display.
		 * @param dwFlags           Currently not used and should be set to 0.
		 * @param pvReserved        Reserved for future use.
		 * @return Returns a pointer to the selected certificate context. If no
		 *         certificate was selected, NULL is returned. When you have finished
		 *         using the certificate, free the certificate context by calling the
		 *         CertFreeCertificateContext function.
		 */
		public static final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004, ENABLE_PROCESSED_OUTPUT = 0x0001;

		int ENABLE_LINE_INPUT = 0x0002;
		int ENABLE_PROCESSED_INPUT = 0x0001;
		int ENABLE_ECHO_INPUT = 0x0004;
		int ENABLE_MOUSE_INPUT = 0x0010;
		int ENABLE_WINDOW_INPUT = 0x0008;
		int ENABLE_QUICK_EDIT_MODE = 0x0040;
		int ENABLE_INSERT_MODE = 0x0020;

		int ENABLE_EXTENDED_FLAGS = 0x0080;

		int ENABLE_VIRTUAL_TERMINAL_INPUT = 0x0200;

		int STD_OUTPUT_HANDLE = -11;
		int STD_INPUT_HANDLE = -10;
		int DISABLE_NEWLINE_AUTO_RETURN = 0x0008;

		// BOOL WINAPI GetConsoleScreenBufferInfo(
		// _In_ HANDLE hConsoleOutput,
		// _Out_ PCONSOLE_SCREEN_BUFFER_INFO lpConsoleScreenBufferInfo);
		void GetConsoleScreenBufferInfo(Pointer in_hConsoleOutput,
				CONSOLE_SCREEN_BUFFER_INFO out_lpConsoleScreenBufferInfo) throws LastErrorException;

		void GetConsoleMode(Pointer in_hConsoleOutput, IntByReference out_lpMode) throws LastErrorException;

		void SetConsoleMode(Pointer in_hConsoleOutput, int in_dwMode) throws LastErrorException;

		Pointer GetStdHandle(int nStdHandle);

		// typedef struct _CONSOLE_SCREEN_BUFFER_INFO {
		// COORD dwSize;
		// COORD dwCursorPosition;
		// WORD wAttributes;
		// SMALL_RECT srWindow;
		// COORD dwMaximumWindowSize;
		// } CONSOLE_SCREEN_BUFFER_INFO;
		class CONSOLE_SCREEN_BUFFER_INFO extends Structure {

			public COORD dwSize;
			public COORD dwCursorPosition;
			public short wAttributes;
			public SMALL_RECT srWindow;
			public COORD dwMaximumWindowSize;

			private static String[] fieldOrder = { "dwSize", "dwCursorPosition", "wAttributes", "srWindow",
					"dwMaximumWindowSize" };

			@Override
			protected java.util.List<String> getFieldOrder() {
				return java.util.Arrays.asList(fieldOrder);
			}

			public int windowWidth() {
				return this.srWindow.width() + 1;
			}

			public int windowHeight() {
				return this.srWindow.height() + 1;
			}
		}

		// typedef struct _COORD {
		// SHORT X;
		// SHORT Y;
		// } COORD, *PCOORD;
		class COORD extends Structure implements Structure.ByValue {
			public COORD() {
			}

			public COORD(short X, short Y) {
				this.X = X;
				this.Y = Y;
			}

			public short X;
			public short Y;

			private static String[] fieldOrder = { "X", "Y" };

			@Override
			protected java.util.List<String> getFieldOrder() {
				return java.util.Arrays.asList(fieldOrder);
			}
		}

		// typedef struct _SMALL_RECT {
		// SHORT Left;
		// SHORT Top;
		// SHORT Right;
		// SHORT Bottom;
		// } SMALL_RECT;
		class SMALL_RECT extends Structure {
			public SMALL_RECT() {
			}

			public SMALL_RECT(SMALL_RECT org) {
				this(org.Top, org.Left, org.Bottom, org.Right);
			}

			public SMALL_RECT(short Top, short Left, short Bottom, short Right) {
				this.Top = Top;
				this.Left = Left;
				this.Bottom = Bottom;
				this.Right = Right;
			}

			public short Left;
			public short Top;
			public short Right;
			public short Bottom;

			private static String[] fieldOrder = { "Left", "Top", "Right", "Bottom" };

			@Override
			protected java.util.List<String> getFieldOrder() {
				return java.util.Arrays.asList(fieldOrder);
			}

			public short width() {
				return (short) (this.Right - this.Left);
			}

			public short height() {
				return (short) (this.Bottom - this.Top);
			}

		}

	}
}

record WindowSize(int rows, int columns) {

}
