package com.mlprograms.searchmax;

/**
 * Modus der Textextraktion aus Dateien.
 */
public enum ExtractionMode {
    POI_ONLY,       // Nur Apache POI verwenden
    TIKA_ONLY,      // Nur Apache Tika verwenden
    POI_THEN_TIKA   // Zuerst POI, falls fehlschlägt Tika als Fallback
}

