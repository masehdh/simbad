package com.simbad2;

import javacard.framework.*;
import sim.toolkit.*;
import sim.access.*;

public class simbad2 extends Applet implements ToolkitInterface, ToolkitConstants {
	
	// Begin by defining the offset in the buffer array and length of bytes for each main menu item
	
	private static final short MAINMENU_OFFSET = (short) 0; // Title begins at index 0 of the buffer array
	private static final short MAINMENU_LENGTH = (short) 11; // "SimBad Home" is 11 characters so 11 bytes, ends at index 11 (inclusive)
	private static final short OFFLINE_OFFSET = (short) MAINMENU_LENGTH; // First menu item begins at index 11 (which makes sense because it starts from 0)
	private static final short OFFLINE_LENGTH = (short) 15; // "Offline Payment" is 15 characters so 15 bytes
	private static final short ONLINE_OFFSET = (short) (OFFLINE_OFFSET+OFFLINE_LENGTH); // Second menu item begins at index 26, we add the previous menu item and title lengths
	private static final short ONLINE_LENGTH = (short) 14; // "Offline Payment" is 14 characters so 14 bytes
	private static final short MY_ACCOUNT_OFFSET = (short) (ONLINE_OFFSET + ONLINE_LENGTH); // "Third menu item offset = the second menu item offset + its length
	private static final short MY_ACCOUNT_LENGTH = (short) 10; // "My Account" is 10 characters so 10 bytes
	private static final short ABOUT_OFFSET = (short) (MY_ACCOUNT_OFFSET + MY_ACCOUNT_LENGTH); 
	private static final short ABOUT_LENGTH = (short) 5; 
	
	// Offline payment menu
	private static final short OFFLINE_MENU_OFFSET = (short) 0;
	private static final short OFFLINE_MENU_LENGTH = (short) 15; // "Offline Payment" is 15 characters so 15 bytes
	private static final short SEND_MONEY_OFFSET = (short) OFFLINE_MENU_LENGTH;
	private static final short SEND_MONEY_LENGTH = (short) 10;
	private static final short RECEIVE_MONEY_OFFSET = (short) (SEND_MONEY_OFFSET + SEND_MONEY_LENGTH);
	private static final short RECEIVE_MONEY_LENGTH = (short) 13;
	private static final short BALANCE_OFFSET = (short) (RECEIVE_MONEY_OFFSET + RECEIVE_MONEY_LENGTH);
	private static final short BALANCE_LENGTH = (short) 7;
	private static final short TRANSACTION_HISTORY_OFFSET = (short) (BALANCE_OFFSET + BALANCE_LENGTH);
	private static final short TRANSACTION_HISTORY_LENGTH = (short) 19;
		
	// Next we write out the byte buffer containing the applet's main menu data strings, all is packed together to save space, look into creating this via a pre-compiler or for it
	// to exist in a GSM file for multiple languages
	
	private static byte[] mainMenuStrings = 
		{ 
				// "Simbad Home"
				'S', 'i', 'm', 'b', 'a', 'd', ' ', 'H', 'o', 'm', 'e',
				// "Offline Payment"
				'O', 'f', 'f', 'l', 'i', 'n', 'e', ' ', 'P', 'a', 'y', 'm', 'e', 'n', 't',
				// "Online Payment"
				'O', 'n', 'l', 'i', 'n', 'e', ' ', 'P', 'a', 'y', 'm', 'e', 'n', 't',
				// "My Account"
				'M', 'y', ' ', 'A', 'c', 'c', 'o', 'u', 'n', 't',
				// "About"
				'A', 'b', 'o', 'u', 't'
		};
	
	// Separating these menus for easier readability, should combine them into one array to save on overhead later
		private static byte[] offlineMenuStrings = 
			{
					// "Offline Payment"
					"O", "f", "f", "l", "i", "n", "e", " ", "P", "a", "y", "m", "e", "n", "t",
					// "Send Money"
					"S", "e", "n", "d", " ", "M", "o", "n", "e", "y",
					// "Receive Money"
					"R", "e", "c", "e", "i", "v", "e", " ", "M", "o", "n", "e", "y",
					// "Balance"
					"B", "a", "l", "a", "n", "c", "e",
					// "Transaction History"
					"T", "r", "a", "n", "s", "a", "c", "t", "i", "o", "n", " ", "H", "i", "s", "t", "o", "r", "y"
					
			};
	
	private short[] mainMenu =
		{
				// Menu's title (offset, length)
				MAINMENU_OFFSET, MAINMENU_LENGTH,
				
				// Menu items (offset, length)
				OFFLINE_OFFSET, OFFLINE_LENGTH,
				ONLINE_OFFSET, ONLINE_LENGTH,
				MY_ACCOUNT_OFFSET, MY_ACCOUNT_LENGTH,
				ABOUT_OFFSET, ABOUT_LENGTH
		};
	private short[] offlineMenu =
		{
				// Menu's title (offset, length)
				OFFLINE_MENU_OFFSET, OFFLINE_MENU_LENGTH,
				
				// Menu items (offset, length)
				SEND_MONEY_OFFSET, SEND_MONEY_LENGTH,
				RECEIVE_MONEY_OFFSET, RECEIVE_MONEY_LENGTH,
				BALANCE_OFFSET, BALANCE_LENGTH,
				TRANSACTION_HISTORY_OFFSET, TRANSACTION_HISTORY_LENGTH
		};
	
	// Need to initialize some variables before calling the constructor
	
	private byte menuEntryId_1; // SIM Root menu item id. Used for register/unregister
	private byte menuEntryId_2; // SIM Root menu item id. Used for register/unregister
    private boolean eventsRegistered;
    private byte[] terminalProfileMask =
    {(byte)0x09,(byte)0x03,(byte)0x21,(byte)0x70,(byte)0x0D}; // Mask defining the SIM Toolkit features required by the Applet. It is a bitmask with 1s reflecting the needed profiles.
    private byte[] tempBuffer; // Volatile RAM temporary buffer for storing intermediate data and results. It is 180 bytes, enough for a long SMS + dialing number.
    private boolean environmentOk = false;
    final static byte VERIFY = (byte) 0x20; // INS (instruction) for pin verification
    final static byte PIN_TRY_LIMIT = (byte) 0x03; // maximum number of incorrect tries before the PIN is blocked
    final static byte MAX_PIN_SIZE = (byte) 0x08; // maximum size PIN
    final static short SW_VERIFICATION_FAILED = 0x6300; // signal that the PIN verification failed
    final static short SW_PIN_VERIFICATION_REQUIRED = 0x6301; // signal the the PIN validation is required for a transaction
    OwnerPIN pin; // instance variable declaration
    
	// Here we call the constructor, only executed once during Applet installation. All memory buffers created here
    
	public simbad2(byte[] bArray, short bOffset, byte bLength) {
        tempBuffer = JCSystem.makeTransientByteArray((short)180, JCSystem.CLEAR_ON_RESET); // Create tempBuffer[] in RAM (to avoid EEPROM/Flash stress due to high update rates
        pin = new OwnerPIN (PIN_TRY_LIMIT, MAX_PIN_SIZE); 
        // Need to read from the command apdu buffer, however, this is likely a TLV and the code below will need to change.
        byte iLen = bArray[bOffset]; // aid length
        bOffset = (short) (bOffset + iLen + 1);
        byte cLen = bArray[bOffset]; // info length
        bOffset = (short) (bOffset + cLen + 1);
        byte aLen = bArray[bOffset]; // applet data length
        pin.update(bArray, (short) (bOffset + 1), aLen); // The installation parameters contain the PIN
        
		ToolkitRegistry reg = ToolkitRegistry.getEntry(); // Register to the SIM Toolkit Framework
		
		// We register the applet under the EVENT_MENU_SELECTION event. So when the applet is first selected, it is registered.
		menuEntryId_1 = reg.initMenuEntry(
				mainMenuStrings, // the array of characters
				MAINMENU_OFFSET, //the very first offset, for the title
				MAINMENU_LENGTH, // the length of the title
				(byte)(0), // Next action to be taken after menu initialized, here 0 means none, can replace this with event names in ToolkitConstants (i.e. PRO_CMD_SET_UP_CALL)
				false, // Indicated if help is available for the menu entry 
				(byte)0, // the preferred value for the icon list qualifier, seems like 0 means no icon list qualifier
				(short)0); // the icon identifier for the menu entry (0 means no icon)
		
		// This is for offline menu
		menuEntryId_2 = reg.initMenuEntry(
				offlineMenuStrings, // the array of characters
				OFFLINE_MENU_OFFSET, //the very first offset, for the title
				OFFLINE_MENU_LENGTH, // the length of the title
				(byte)(0), // Next action to be taken after menu initialized, here 0 means none, can replace this with event names in ToolkitConstants (i.e. PRO_CMD_SET_UP_CALL)
				false, // Indicated if help is available for the menu entry 
				(byte)0, // the preferred value for the icon list qualifier, seems like 0 means no icon list qualifier
				(short)0); // the icon identifier for the menu entry (0 means no icon)
		
		// Register the other events used by the applet
        reg.setEvent(EVENT_PROFILE_DOWNLOAD);
        eventsRegistered = true;
	};
	
	// Next we define the install method. This is called by the JCRE when the Applet is first installed. The bArray parameter contains installation parameters. These
	// usable parameters are stored as LV-pairs (1 byte = data length, followed by parameter data itself). These installation parameters follow this order (data pairs start at
	// offset "bOffset"):
	// 1. Applet's instance AID
	// 2. Control information ?
	// 3. Applet data
	public static void install(byte bArray[], short bOffset, byte bLength) {
		
		// Create the applet instance
		simbad2 Simbad2 = new simbad2();
		
		// Register the Applet's instance with the JCRE. Argument here is the AID parameter of the installation parameters
		Simbad2.register(bArray, (short) (bOffset + 1), (byte) bArray[bOffset]); // bOffset + 1 because we want to skip the length byte and get straight to the value, bArray[bOffset] is the length
	}
	
	// Method called by the SIM Toolkit Framework to trigger the applet
	public void processToolkit(byte event) {
		
		// Define the SIM Toolkit session handler variables
		EnvelopeHandler envHdlr;
		ProactiveHandler proHdlr;
		ProactiveResponseHandler rspHdlr;
		
		EnvelopeResponseHandler envRspHdlr;
		
		//Begin the case-switch, which acts as a dispatcher for different methods to be called depending on the contents of the apdu received from the phone
		switch(event) {
		
			// The event EVENT_PROFILE_DOWNLOAD happens after installation and allows us to check if the SIM card and phone's functionality are good enough for our application
			case EVENT_PROFILE_DOWNLOAD:
				environmentOk = testAppletEnvironment();
				if (environmentOk) { //this means the phone's and SIM's capabilities are ok
					if (!eventsRegistered)
						registerEvents(); // Applet can now respond to events and shows up in the phone's root menu
				}
				else {
					if (eventsRegistered)
						clearEvents(); // Applet no longer receives events and does not show up in the phone's root menu
				}
				break;
			
			// If someone selects the root menu
			case EVENT_MENU_SELECTION:
				
				// Get the references of the required SIM Toolkit session handlers 
				proHdlr = ProactiveHandler.getTheHandler();
				envHdlr = EnvelopeHandler.getTheHandler();
				
				// Get the identifier of the SIM Toolkit menu item selected by the user
				byte itemId = envHdlr.getItemIdentifier();
				// If selected item identifier matches a registered menu item identifier
				if (itemId == menuEntryId_1) {
					RootMenuHandler();
				}
				break;	
		};
	};
	
	// Method called by the JCRE, once selected. In this case, method is not implemented as our applet only processes SIM Toolkit events. This may need to change.
	// But for now has to be implemented as an empty method
	public void process(APDU apdu) {}
	
	// Auxiliary methods called by dispatcher start here
	
	// We begin defining all the auxiliary methods that the case-switch dispatcher calls. For now, writing in default things to return in place of actual items.
	public void RootMenuHandler() {
		while(true) {
			byte item = displayMenu(mainMenuStrings, mainMenu, (byte) 0);
			if (item < 0 || item >= 4) // Error or 'exit' selected?
				break;
			
			switch (item) {
				case 0: // Offline Payment selected
					//function to open offline payment menu goes here
					offlineMenuHandler();
					break;
				case 1: // Online Payment selected
					//function to open online payment menu goes here
					break;
				case 2: // My Account selected
					//function to open my account menu goes here
					break;
				case 3: // About selected
					//function to open about menu/page goes here
					break;
			};
		};
	};
	
	public void offlineMenuHandler() {
		while(true) {
			byte item = displayMenu(offlineMenuStrings, offlineMenu, (byte) 0);
			if (item < 0 || item >= 4) // Error or 'exit' selected?
				break;
			switch (item) {
			case 0: // Send Money selected
				// Function to continue with send money goes here
				
				break;
			case 1: // Receive Money selected
				// Function to continue with receive money goes here
				break;
			case 2: // Balance selected
				// Function to continue with balance goes here
				break;
			case 3: // Transaction history selected
				// Function to continue with transaction history goes here
			}
		}
	}
		
	//Test applet environment
	//Tests whether the phone supports SIM toolkit functionalities that are required by the applet. The card's file system contains the files required by the applet
	//It returns a boolean, true if both the card and the phone comply with the applet's requirements, false otherwise
	
	private boolean testAppletEnvironment() {
		// The .check method with one argument can be used to see if the phone supports certain commands, for example the PRO_CMD_xxx constants
		// Here we use a bit-mask array to check multiple features at once, we expect features with respective bit, set to "1", to exist. Bit-0 in the array refers to the
		// existence of function 0 and so on.
		
		return (MEProfile.check(terminalProfileMask, (short)0, (short)terminalProfileMask.length));
	}
	
	//Register events
	//Registers the events used by the applet (except EVENT_PROFILE_DOWNLOAD which is not cleared) and sets the eventsRegistered flag to "true"
	private void registerEvents () {
		ToolkitRegistry reg = ToolkitRegistry.getEntry();
		// Enable EVENT_MENU_SELECTION for menuEntryId_1
		reg.enableMenuEntry(menuEntryId_1);
		// Register EVENT_STATUS_COMMAND every 30 seconds (not used)
		// reg.requestPollInterval((short)30);
		// Register all the other events we listen to in the Applet:
		// insert them here when ready
        // Set the eventsRegistered flag (no exception happened before it)
        eventsRegistered = true;		
	}
	
	// Clear events
	// Clears the events used by the Applet (except EVENT_PROFILE_DOWNLOAD so that the Applet can continue testing its environment and register the events if a compliant 
	// environment is detected
	// Also sets the eventsRegistered flag to 'false'
	private void clearEvents() {
		ToolkitRegistry reg = ToolkitRegistry.getEntry();
		// Disable EVENT_MENU_SELECTION for menuEntryId_1
		reg.disableMenuEntry(menuEntryId_1);
        // Clear all the other events used by the Applet
		// insert them here when ready
		// Set the eventsRegistered to false
		eventsRegistered = false;
	}
	
	
	// Display menu
	public byte displayMenu(byte[] menuDataBuffer, short[] menuDefinition, byte selectedItemIndex) {
		short menu_ofs, menu_exit, item_start_ofs, item_length;
		ProactiveHandler proHdlr = ProactiveHandler.getTheHandler();

		// Pre menu setup
		proHdlr.init(PRO_CMD_SELECT_ITEM, (byte)0x080, DEV_ID_ME);
		
		// Menu header
		proHdlr.appendTLV(TAG_ALPHA_IDENTIFIER, menuDataBuffer, menuDefinition[0], menuDefinition[1]);
		short menu_data_offset = (short) 2; // Skip the type and length, straight to the value
		byte item_index = (byte) 0; // 0 = first item
		
		// Menu items
		while (menu_data_offset < menuDefinition.length) {
			proHdlr.appendTLV((byte)(TAG_ITEM | TAG_SET_CR), item_index, 
					menuDataBuffer,  // Menu item text buffer (i.e. menuStrings
					menuDefinition[menu_data_offset], // Offset
					menuDefinition[(short)(menu_data_offset+1)]); // length
			item_index++;
			
			menu_data_offset+= 2;
		}
		
		// pre-selected item (where the "cursor" was placed)
		if (selectedItemIndex >= 0) // If we have a given start index >= 0
			proHdlr.appendTLV(TAG_ITEM_IDENTIFIER, selectedItemIndex);
		if ((selectedItemIndex = proHdlr.send()) < 0)
			return selectedItemIndex; // Negative error code
		ProactiveResponseHandler prh = ProactiveResponseHandler.getTheHandler();
		if (prh.getGeneralResult() != 0)
			return -2; // Return -2 if general result fails
		return (byte) prh.getItemIdentifier(); // becomes index 0..N-1
	}
}































































