# Kurs-Paket aus dem Lern-Monitor exportieren

Der Lern-Monitor lässt sich nur aus einem Chat ändern, der **von seiner eigenen Seite aus** geöffnet wurde.
Dort diesen Text einfügen (einmalig, kurze Aufgabe):

> Bitte im Lern-Monitor zwei Ersetzungen vornehmen und als neue Version veröffentlichen (Version 31 – Kurs-Paket für die Android-App). Sonst nichts ändern.
>
> **Ersetzung 1** – suche:
> ```
> <div class="history" id="history"><p class="meta">Hier erscheinen deine Ergebnisse, sobald du das erste Quiz abgeschlossen hast.</p></div>
>     </div>
>   </section>
> ```
> ersetze durch:
> ```
> <div class="history" id="history"><p class="meta">Hier erscheinen deine Ergebnisse, sobald du das erste Quiz abgeschlossen hast.</p></div>
>     </div>
>     <div class="panel" id="app-export-panel">
>       <span class="label">Für die Android-App</span>
>       <p class="meta">Lädt alle Lernziele mit Lernkarten, alle Fragen und alle Fachbegriffe als Kurs-Paket (JSON). In der App: „Kurs-Paket importieren“. Die Datei mit Pflichtbuch ist nur für dich.</p>
>       <div class="row" style="flex-wrap:wrap;gap:8px">
>         <button class="btn ghost" type="button" id="app-export">Kurs-Paket herunterladen</button>
>         <button class="btn ghost" type="button" id="app-export-buch">Kurs-Paket mit Pflichtbuch herunterladen</button>
>       </div>
>       <p class="meta" id="app-export-msg"></p>
>     </div>
>   </section>
> ```
>
> **Ersetzung 2** – suche:
> ```
>   $("#c-dl-loe").addEventListener("click", function () { download(true); });
> ```
> ersetze durch:
> ```
>   $("#c-dl-loe").addEventListener("click", function () { download(true); });
>   function appExport(withBuch) {
>     var msg = $("#app-export-msg");
>     if (!state.downloads) { msg.textContent = "Herunterladen geht hier nicht. Öffne den Lern-Monitor auf claude.ai im Browser."; return; }
>     if (!state.loadedLz || !state.loadedF) { msg.textContent = "Die Inhalte laden noch – bitte gleich noch einmal tippen."; return; }
>     var cp = function (o) { return JSON.parse(JSON.stringify(o)); };
>     var pkg = { format: "lernapp-kurs/1", erstellt: new Date().toISOString(), kurs: { id: "fachwirt-gus", titel: "Geprüfte/r Fachwirt/in im Gesundheits- und Sozialwesen (IHK)" },
>       lernziele: state.lzs.map(cp), fragen: state.fragen.map(cp), vokabeln: (state.vok || []).map(cp) };
>     msg.textContent = withBuch ? "Das Pflichtbuch wird zusammengestellt …" : "Wird erstellt …";
>     var go = withBuch ? buchIndex().then(function (idx) { return Promise.all(idx.map(function (k) { return buchFetch(k.kap); })).then(function (kaps) { pkg.buch = { index: idx, kapitel: kaps }; }); }) : Promise.resolve();
>     go.then(function () {
>       return state.downloads.save({ filename: (withBuch ? "kurs-paket-mit-buch-" : "kurs-paket-") + TODAY + ".json", data: new Blob([JSON.stringify(pkg)], { type: "application/json" }) });
>     }).then(function () {
>       msg.textContent = "Fertig: " + pkg.lernziele.length + " Lernziele, " + pkg.fragen.length + " Fragen, " + pkg.vokabeln.length + " Fachbegriffe" + (pkg.buch ? ", " + pkg.buch.kapitel.length + " Buchkapitel" : "") + ".";
>     }, function (e) { msg.textContent = "Das hat nicht geklappt: " + ((e && (e.message || e.code)) || e); });
>   }
>   $("#app-export").addEventListener("click", function () { appExport(false); });
>   $("#app-export-buch").addEventListener("click", function () { appExport(true); });
> ```

Danach im Lern-Monitor auf der Startseite ganz unten **„Kurs-Paket herunterladen“** → Datei landet im Download-Ordner → in der App **„Kurs-Paket importieren“**.

Format `lernapp-kurs/1`: `{ format, erstellt, kurs:{id,titel}, lernziele:[…], fragen:[…], vokabeln:[…], buch? }` – dieselben Felder wie in der Datenbank des Lern-Monitors (Lernkarte, Erwartungshorizont, Musterlösung, Rechenwerte mit `bereich` für wechselnde Zahlen usw.).
