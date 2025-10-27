package com.mlprograms.searchmax.view;

/**
 * Zentrale Sammlung aller in der GUI angezeigten Texte.
 */
public final class GuiConstants {

    public static final String TITLE_SEARCHMAX = "SearchMax";

    // DrivePanel
    public static final String DRIVE_PANEL_TITLE = "Search Drives";

    // TopPanel
    public static final String BROWSE_BUTTON = "Browse";
    public static final String CASE_SENSITIVE = "Match case";
    public static final String SEARCH_BUTTON = "Search";
    public static final String CANCEL_BUTTON = "Cancel";
    public static final String MANAGE_FILTERS = "Manage filters";
    public static final String BUTTON_LOGS = "Logs";
    public static final String LABEL_FOLDER = "Folder";
    public static final String LABEL_SEARCHTEXT = "Search text";
    public static final String FILES_FOUND = "Files found";

    // SearchView / JFileChooser / JOptionPane messages
    public static final String CHOOSER_SELECT_FOLDER = "Select folder";
    public static final String MSG_ENTER_QUERY_OR_TYPE = "Please enter a search text or specify a filter.";
    public static final String MSG_MISSING_INPUT_TITLE = "Input missing";
    public static final String MSG_PLEASE_START_FOLDER = "Please specify a start folder or select a drive.";
    public static final String MSG_PLEASE_QUERY_OR_TYPE_OR_FILTER = "Please enter a search text, file type, or a filter.";

    // BottomPanel / Status
    public static final String STATUS_READY = "Ready";
    public static final String PERFORMANCE_MODE = "Performance mode";
    public static final String STATUS_LABEL_PREFIX = "Status: ";
    public static final String SEARCH_RUNNING_TEXT = "Searching";

    // FiltersDialog
    public static final String FILTERS_DIALOG_TITLE = "Manage filename filters";
    public static final String FILTERS_PANEL_TITLE = "Filename filters";
    public static final String TAB_ALLOW = "Allow";
    public static final String TAB_DENY = "Exclude";
    public static final String RADIO_ANY = "At least one filter must match";
    public static final String RADIO_ALL = "All filters must match";
    public static final String BUTTON_ADD = "Add";
    public static final String BUTTON_ENABLE_ALL = "Enable all";
    public static final String BUTTON_DISABLE_ALL = "Disable all";
    public static final String INPUT_ADD_PATTERN = "New pattern (e.g. part of filename):";
    public static final String INPUT_ADD_TITLE = "Add";

    public static final String EXT_PANEL_TITLE = "File type";
    public static final String INPUT_NEW_EXTENSION = "New file type (e.g. .txt):";
    public static final String MSG_EXTENSION_EXISTS = "Extension already exists.";
    public static final String MSG_ERROR_TITLE = "Error";

    public static final String BUTTON_OK = "OK";

    // Content panel (neu)
    public static final String CONTENT_PANEL_TITLE = "File content";
    public static final String INPUT_ADD_CONTENT_PATTERN = "New content pattern (e.g. text inside file):";

    // Table column constants (English)
    public static final String COLUMN_ACTIVE = "Active";
    public static final String COLUMN_PATTERN = "Pattern";
    public static final String COLUMN_CASE_SENSITIVE = "Case sensitive";
    public static final String COLUMN_REMOVE = "Remove";

    // Extensions specific column
    public static final String EXT_COLUMN_EXTENSION = "Extension";

    // Additional column labels
    public static final String COLUMN_EXCLUDE = "Exclude";

}
