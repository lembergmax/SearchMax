# SearchMax

SearchMax is a desktop application for fast, flexible file discovery and optional file extraction. It provides filtering by filename, file content, and file extensions, along with tools for exporting or copying matched results.

---

## Table of Contents

* Overview
* Key Features
* Typical Workflow
* Screenshots *(optional)*
* User Interface Walkthrough
* Tips & Best Practices
* Troubleshooting *(optional)*
* License

---

## Overview

SearchMax helps power users, developers, and administrators locate files quickly across multiple directories or drives. Searches can be refined using filename patterns, content-based filters, and extension allow/deny lists. If desired, matched files can be copied or exported using customizable extraction rules.

The interface is structured into drive selection, filtering controls, extraction settings, and a results panel for efficient workflow.

---

## Key Features

* Clear and responsive GUI with search input, progress display, and results table.
* Search across multiple drives and folders simultaneously.
* Filename and content filters:

    * Wildcard and pattern-based filename matching.
    * Include/exclude rules for file content or names.
* Extension allow/deny lists for targeted or restricted searches.
* Optional extraction:

    * Copy/export matched files to a destination of your choice.
    * Options for structure preservation, conflict handling, and overwrite behavior.
* Cancellable and non-blocking search process with live progress updates.
* Results table with batch operations (open folder, copy path, export selected files).
* Built-in log viewer for errors, permission issues, and diagnostic details.

---

## Typical Workflow

1. Select one or more drives or folders as the search scope.
2. Define filename filters and optional content filters.
3. Adjust extension allow/deny lists if needed.
4. (Optional) Configure extraction settings.
5. Start the search and monitor progress in real time.
6. Review results and perform actions such as opening locations or exporting files.

---

## Screenshots

* Main window
![](screenshots\main_window.png)

* Drive selection panel
![](screenshots\drive_panel.png)

* Filter and extension configuration dialog
![](screenshots\filters_dialog.png)

* Extraction settings dialog
![](screenshots\extraction_settings.png)

* Log viewer
![](screenshots\log_viewer.png)

---

## User Interface Walkthrough

### Drive Selection

Choose one or more drives or directories to include in the search. If access to a location is restricted, the application logs a warning and continues with allowed paths.

### Search Criteria & Filters

Enter filename patterns or keywords. Add include/exclude filters for content or filenames. Enable or disable filters individually to test different configurations.

### Extension Allow/Deny Lists

Specify which file types to include or exclude. Useful for narrowing searches to text files or ignoring large binary formats.

### Extraction Settings *(optional)*

If exporting matched files:

* Choose destination folder.
* Preserve directory structure or flatten output.
* Configure conflict handling (skip, overwrite, rename).

### Running a Search & Monitoring Progress

Searching runs in the background. The status panel shows the current file, processed counts, and elapsed time. The search can be cancelled at any time.

### Results & Available Actions

Search results appear in a sortable table. You can:

* Open file locations
* Copy paths to clipboard
* Extract selected files (if enabled)

### Logging & Diagnostics

The log viewer provides informational messages, warnings, errors, and access issues encountered during the search.

---

## Tips & Best Practices

* Start with broader filters, then refine.
* Use extension filters to avoid scanning large binary files unless necessary.
* Disable filters temporarily to test how they affect results.
* Use the log viewer to diagnose access-related issues on restricted drives.

---

## License

*TODO: Add license information (e.g., MIT, Apache-2.0, GPL, etc.).*
