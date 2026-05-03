package com.texteditor.akshara;

import java.io.IOException;
import java.util.Arrays;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;

public class Akshara {

	public static final String APP_NAME = "Akshara";
	public static LibC.Termios defaultAttributes;
	public static int rows = 10;
	public static int columns = 10;
	public static String VERSION = "v1";

	public static void main(String[] args) throws IOException {
		System.out.println("Hello World!!!");

		enableRawMode();
		initEditor();

		while (true) {

			//refreshScreen();
			int key = readKey();
			handleKey(key);

		}
	}

	private static void initEditor() {
		LibC.Winsize winsize = getWindowSize();
		columns = winsize.ws_col;
		rows = winsize.ws_row;
	}

	private static void refreshScreen() {
		
		StringBuilder builder =  new StringBuilder();
		
		//We use ANSI escape codes to manipulate the screen 
		
		builder.append("\033[2J"); 
		builder.append("\033[H");

		for (int i = 0; i < rows - 1; i++) {
			builder.append(">\r\n");
		}
		
		String statusbar = APP_NAME + " - " + VERSION;
		builder.append("\033[7m")
				.append(APP_NAME)
				.append(" - ")
				.append(VERSION)
				.append(" ".repeat(Math.max(0, columns - statusbar.length())))
				.append("\033[0m");
		
		builder.append("\033[H");
		
		System.out.println(builder);
	}

	private static void handleKey(int key) {
		if (key == 'q') {
			System.out.print("\033[2J");
			System.out.print("\033[H");
			LibC.INSTANCE.tcsetattr(LibC.SYSTEM_IN_FD, LibC.TCSAFLUSH, defaultAttributes);
			System.exit(1);
		} else {
			System.out.print(((char)key) + "\r\n");
		}

	}

	private static int readKey() throws IOException {
		int key = System.in.read();
		//check if the input is a escape sequence 
//		if(key != '\033') {
//			return key;
//		}
//		
//		int nextKey = System.in.read();
//		if(nextKey != '[') {
//			return nextKey;
//		}
//		
//		int anotherKey = System.in.read();
//		switch(anotherKey) {
////		case 'A' -> return ARROW_UP;
////		case 'B' -> return ARROW_DOWN;
//		default -> return key;
//		}
		return key;
	}

	private static void enableRawMode() {
		// Getting the current attributes of the system
		LibC.Termios termios = new LibC.Termios();
		int returnCode = LibC.INSTANCE.tcgetattr(LibC.SYSTEM_IN_FD, termios);
		
		//Saving the copy of the current attributes of the terminal to restore cooked mode  
		defaultAttributes = LibC.Termios.of(termios);

		if (returnCode != 0) {
			// internal error
			System.err.println("Some error occured ");
			System.exit(1);
		}

		// With the help of bitwise operation we negate the values, such has turn off echoing,
		// canonical,implementation-defined input processing and signals
		termios.c_lflag &= ~(LibC.ECHO | LibC.ICANON | LibC.IEXTEN | LibC.ISIG);
		//Disable flow and stop the translations
		termios.c_iflag &= ~(LibC.IXON | LibC.ICRNL);
		//disable output post-processing
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
			System.exit(1);
		}

		return winsize;

	}
}



/*
 * This interface will help use make direct Linux syscalls i.e., provide an
 * interface between a process and the operating system
 */
interface LibC extends Library {
	
	//This is a constant that represents the default data stream input (stdin), here the kernel sees 0
	//Terminal configurations need a file descriptor that points to the terminal device
	int SYSTEM_IN_FD = 0;
	
	//These are octal bitmask values from Linux kernel headers, 
	//meaning these are just octal values that represent these in the terminal setting 
	
	/* from Linux kernel asm/termbits.h */
	/*#define ISIG    0000001  -> octal 
	* #define ICANON  0000002
	* #define ECHO    0000010
	* #define IEXTEN  0100000
	* #define IXON    0002000
	* #define ICRNL   0000400
	* #define OPOST   0000001 */
	
	
	int ISIG = 1, ICANON = 2, ECHO = 10, TCSAFLUSH = 2, IXON = 2000, ICRNL = 400, IEXTEN = 100000, OPOST = 1, VMIN = 6,
			VTIME = 5;
	
	//Stands for Terminal IO Control Get WINdow SiZe is a ioctl request code we need 
	//to pass on the ioctl to get the window size
	int  TIOCGWINSZ = 0x5413;
	
	
	/*
	 * This creates a dynamic proxy object and every method call on INSTANCE becomes
	 * a real native syscall under the hood
	 */
	LibC INSTANCE = Native.load("c", LibC.class);
	
	//This mirrors the termios struct in memory so we can pass it to the sys calls
	//@Structure.FieldOrder tell jna the memory layout since in C every thing is one block of memory
	
	@Structure.FieldOrder(value = { "c_iflag", "c_oflag", "c_cflag", "c_lflag", "c_cc" })
	class Termios extends Structure {
		//copied from the man page as it is 
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
			clone.c_cc = t.c_cc;
			return clone;
		}

		@Override
		public String toString() {
			return "Termios [c_iflag=" + c_iflag + ", c_oflag=" + c_oflag + ", c_cflag=" + c_cflag + ", c_lflag="
					+ c_lflag + ", c_cc=" + Arrays.toString(c_cc) + "]";
		}
	}
	
	//Mirrors the winsize struct in <sys/ioctl.h> to get the window size 
	
	@Structure.FieldOrder(value = { "ws_row", "ws_col", "ws_xpixel", "ws_ypixel" })
	public class Winsize extends Structure {
		public short ws_row, ws_col, ws_xpixel, ws_ypixel;
	}
	
	//Native methods 
	
	//get the default terminal attributes
	int tcgetattr(int fd, Termios termios);
	
	//set the terminal attributes
	int tcsetattr(int fd, int optional_actions, Termios termios);
	
	//input and output control is the system call under Device management, device specific operations
	int ioctl(int fd, int opt, Winsize winze);

}