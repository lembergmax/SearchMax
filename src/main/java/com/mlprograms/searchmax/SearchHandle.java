package com.mlprograms.searchmax;

import lombok.Getter;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verwaltet den Status und die Aufgaben eines parallelen Suchvorgangs.
 * Hält Referenzen auf laufende ForkJoinTasks, Zähler für verbleibende Aufgaben,
 * Trefferanzahl und Suchergebnisse.
 */
@Getter
public final class SearchHandle {

    /**
     * Die aktuell laufende Hauptaufgabe.
     */
    private volatile ForkJoinTask<?> task;

    /**
     * Sammlung aller gestarteten ForkJoinTasks.
     */
    private final Collection<ForkJoinTask<?>> tasks = new ConcurrentLinkedQueue<>();

    /**
     * Kennzeichnet, ob die Suche abgebrochen wurde.
     */
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * Startzeitpunkt der Suche in Nanosekunden.
     */
    private final long startNano;

    /**
     * Zähler für verbleibende Aufgaben.
     */
    private final AtomicInteger remainingTasks;

    /**
     * Zähler für gefundene Treffer.
     */
    private final AtomicInteger matchCount;

    /**
     * Sammlung der gefundenen Ergebnisse.
     */
    private final Collection<String> results;

    /**
     * Anzahl der anfänglichen Aufgaben.
     */
    private final int initialTasks;

    /**
     * Zähler für insgesamt verarbeitete Dateien.
     */
    private final AtomicInteger totalFiles = new AtomicInteger(0);

    /**
     * Zähler für aktuell verarbeitete Dateien.
     */
    private final AtomicInteger processedFiles = new AtomicInteger(0);

    /**
     * Erstellt ein neues SearchHandle mit den gegebenen Parametern.
     *
     * @param task           Die Hauptaufgabe der Suche
     * @param startTimeNano  Startzeitpunkt in Nanosekunden
     * @param remainingTasks Zähler für verbleibende Aufgaben
     * @param matchCount     Zähler für Treffer
     * @param results        Sammlung der Suchergebnisse
     * @param initialTasks   Anzahl der anfänglichen Aufgaben
     */
    public SearchHandle(final ForkJoinTask<?> task, final long startTimeNano, final AtomicInteger remainingTasks, final AtomicInteger matchCount, final Collection<String> results, final int initialTasks) {
        this.startNano = startTimeNano;
        this.remainingTasks = remainingTasks;
        this.matchCount = matchCount;
        this.results = results;
        this.initialTasks = initialTasks;
        setTask(task);
    }

    /**
     * Setzt die aktuelle Hauptaufgabe und fügt sie der Aufgabensammlung hinzu.
     *
     * @param task Die zu setzende ForkJoinTask
     */
    public void setTask(final ForkJoinTask<?> task) {
        this.task = task;
        addTask(task);
    }

    /**
     * Fügt eine ForkJoinTask der Aufgabensammlung hinzu, sofern sie nicht null ist.
     *
     * @param task Die hinzuzufügende ForkJoinTask
     */
    public void addTask(final ForkJoinTask<?> task) {
        if (task != null) {
            tasks.add(task);
        }
    }

}
