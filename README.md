# carSim2 – symulacja ruchu drogowego

Wielowątkowa symulacja ruchu samochodowego w Javie 17, działająca na rzeczywistym grafie dróg (skrzyżowania + odcinki dróg wczytywane z plików CSV). Tysiące aut jeździ po mieście w ciągu symulowanej doby, reaguje na korki, światła, pogodę i fotoradary, a każde przejechanie obok fotoradaru jest zapisywane do pliku `violations.csv`. Dzięki temu projekt generuje zbiór danych o prędkościach kierowców w zależności od cech drogi, pogody i natężenia ruchu, który można wykorzystać do dalszej analizy lub uczenia modeli ML.

## Najważniejsze funkcje

- **Graf dróg z CSV** – skrzyżowania (węzły) i odcinki dróg (krawędzie, jedno- i dwukierunkowe).
- **Wyznaczanie tras algorytmem A\*** – z kosztem uwzględniającym typ drogi, limit prędkości, liczbę pasów i aktualne zagęszczenie ruchu. Użyto ważonego A\* (waga heurystyki 1.5), więc trasa nie zawsze jest najkrótsza, ale obliczenia są szybsze.
- **Cache tras** – wyniki A\* są współdzielone między samochodami (`ConcurrentMap<RouteKey, List<Way>>`).
- **Model jazdy** – przyspieszanie, hamowanie przed skrzyżowaniem lub fotoradarem, wpływ agresywności kierowcy, korków i pogody na prędkość.
- **Korki i ponowne wyznaczanie trasy** – gdy auto za długo stoi zablokowane, szuka objazdu; po wyczerpaniu prób rezygnuje z podróży.
- **Światła na skrzyżowaniach** – prosty cykl, w którym zielone światło dostaje po kolei każdy wjazd (60 s na wjazd).
- **Pogoda** – słoneczna, deszczowa i śnieżna; lokalne zdarzenia pogodowe wokół losowych skrzyżowań, zależne od pory roku.
- **Fotoradary** – losowo na ok. 10% odcinków; auta mogą być ich „świadome” i zwalniać.
- **Asynchroniczny zapis wyników** – osobny wątek zapisuje rekordy z kolejki do `violations.csv`.
- **Dobowy profil natężenia ruchu** – docelowa liczba aut zmienia się z godziną (szczyty poranny i popołudniowy).

## Struktura projektu

| Plik | Rola |
|---|---|
| `Main.java` | Punkt wejścia. Wczytuje dane, buduje graf, uruchamia pętlę symulacji (krok = 1 s), zarządza liczbą aut, uruchamia wątek zapisujący wyniki, wypisuje telemetrię i czasy faz. Zawiera też klasę `Record` (jeden zapis z fotoradaru). |
| `DataLoader.java` | Wczytywanie CSV (OpenCSV), parsowanie z wartościami domyślnymi dla brakujących danych (`unknown`), łączenie odcinków ze skrzyżowaniami (`linkWays`). |
| `Intersection.java` | Skrzyżowanie: współrzędne, listy dróg wjazdowych i wyjazdowych, logika świateł (`hasGreenLight`), `getWaysDeep` do wyznaczania obszaru wpływu pogody. |
| `Way.java` | Odcinek drogi: limit prędkości, długość, liczba pasów, krzywizna, nawierzchnia, typ, fotoradar, liczba aut (`AtomicInteger`), zagęszczenie, pogoda. Liczy bazową prędkość przepływu, współczynnik ruchu i pogody oraz sprawdza zapełnienie. |
| `Car.java` | Pojedynczy kierowca: wybór losowej trasy, spawn, przyspieszanie i hamowanie, reakcja na światła, korki i fotoradary, reroute, raportowanie przejazdu przy fotoradarze. |
| `AStar.java` | Wyszukiwanie tras (ważony A\*) z karą za osiedlowe drogi, wolne odcinki i zatłoczenie. |
| `RouteKey.java` | Klucz cache’u tras (id startu + id celu). |
| `Weather.java` | Zarządzanie pogodą: typy pogody, wybór zależny od sezonu, tworzenie i wygasanie lokalnych zdarzeń. |
| `WeatherEvent.java` | Pojedyncze zdarzenie pogodowe (epicentrum, typ, czas zakończenia, dotknięte odcinki). |
| `Camera.java` | Znacznik fotoradaru przypisany do odcinka. |
| `Gaussian.java` | Losowy mnożnik agresywności kierowcy (rozkład normalny, średnia 1.0, odchylenie 0.0833, obcięty do 0.75–1.25). |

## Jak to działa

### Pętla symulacji
Każdy krok odpowiada 1 sekundzie symulowanego czasu. W kroku:
1. aktualizowane są zdarzenia pogodowe, a o 5:00 losowane są nowe,
2. usuwane są auta, które dotarły do celu,
3. liczba aut płynnie dąży do docelowej wartości dla danej godziny (`TARGETS`, zmiana o `transitionSpeed` aut na krok),
4. brakujące auta są tworzone równolegle (trasy liczone przez A\*),
5. wszystkie auta wykonują ruch równolegle (`parallelStream`).

### Model ruchu
- Prędkość pożądana = prędkość bazowa odcinka × agresywność kierowcy × współczynnik ruchu × współczynnik pogody.
- Współczynnik ruchu maleje wykładniczo z gęstością aut na odcinku, a współczynnik pogody wynosi 0.75 dla śniegu, 0.85 dla deszczu i 1.1 dla słońca.
- Auto hamuje tak, by osiągnąć zadaną prędkość w punkcie docelowym odcinka (koniec odcinka przy czerwonym świetle lub korku, miejsce fotoradaru dla kierowców „świadomych” kamery).
- Odcinek jest pełny, gdy liczba aut przekroczy 133 auta/km/pas. Do pełnego odcinka auta nie wjeżdżają.
- Po ok. 90–140 s postoju w korku auto próbuje wyznaczyć nową trasę (maks. 5 prób, potem rezygnuje).

### Koszt trasy w A\*
Koszt odcinka = długość × kara. Kara rośnie dla odcinków o niskim limicie prędkości, dla dróg `residential`/`construction` (×3) oraz `tertiary`/`unclassified` (×1.5), a maleje dla dróg wielopasmowych. Dodatkowo mocno rośnie wraz z obłożeniem odcinka (×50 dla pełnego). Heurystyka to płaskie przybliżenie odległości na sferze.

## Dane wejściowe

Ścieżki do plików są obecnie wpisane na stałe w `Main.java` (zmienne `skrzyzowania` i `drogi`).

**`intersections.csv`** (nagłówek + 3 kolumny):

| Kolumna | Opis |
|---|---|
| 0 | `id` skrzyżowania |
| 1 | szerokość geograficzna |
| 2 | długość geograficzna |

**`road_segments.csv`** (nagłówek + 12 kolumn):

| Kolumna | Opis |
|---|---|
| 0 | `way_id` |
| 1 | limit prędkości (km/h) |
| 2 | nawierzchnia |
| 3 | rzeczywista długość odcinka (m) |
| 4 | długość w linii prostej (m), używana do liczenia krzywizny |
| 5 | czy jednokierunkowa (`yes`/`no`) |
| 6 | czy oświetlona (`yes`/`no`) |
| 7 | typ drogi (np. `primary`, `residential`) |
| 8 | liczba pasów |
| 9 | gładkość nawierzchni |
| 10 | `id` skrzyżowania początkowego |
| 11 | `id` skrzyżowania końcowego |

Brakujące lub niepoprawne wartości (`unknown`) są zastępowane domyślnymi: limit 50 km/h, 1 pas, droga dwukierunkowa i oświetlona.

## Dane wyjściowe

Plik `violations.csv` zawiera po jednym wierszu na każde przejechanie punktu z fotoradarem:

`time, speed, limit, curvature, true_length, way_type, smoothness, surface, is_one_way, is_lit, is_primary, lanes_no, way_id, weather, density`

Prędkości (`speed`, `limit`) są podawane w m/s, `density` to zagęszczenie ruchu na odcinku w momencie przejazdu.

## Wymagania i uruchomienie

- Java 17+
- Maven
- OpenCSV 5.9 (pobierany automatycznie przez Maven)

```bash
# kompilacja
mvn clean compile

# uruchomienie (po ustawieniu poprawnych ścieżek do CSV w Main.java)
mvn compile exec:java -Dexec.mainClass=Main
```

Projekt można też uruchomić bezpośrednio z IntelliJ IDEA (klasa `Main`).

Symulacja domyślnie obejmuje 24 godziny (`simulationSteps = 3600 * 24`). W konsoli co symulowaną minutę wypisywana jest telemetria (liczba aut i cel), a na końcu czasy poszczególnych faz i rozmiar cache’u tras.

## Konfiguracja

Parametry zmieniane w kodzie:

| Parametr | Miejsce | Znaczenie |
|---|---|---|
| `TARGETS` | `Main` | docelowa liczba aut dla każdej godziny doby |
| `transitionSpeed` | `Main` | tempo zmiany liczby aut (auta/s) |
| `simulationSteps` | `Main` | długość symulacji w sekundach |
| `weight` | `AStar` | waga heurystyki (1.0 = klasyczny A\*) |
| `randomHasCamera()` | `Way` | odsetek odcinków z fotoradarem (10%) |
| `greenDuration` | `Intersection` | czas zielonego światła na wjazd |
| `baseAcceleration`, `deacceleration` | `Car` | parametry przyspieszania i hamowania |
| `MAX_REROUTE_ATTEMPTS` | `Car` | liczba prób ponownego wyznaczenia trasy |

## Znane ograniczenia i plany

- Ścieżki do plików CSV są zapisane na stałe w kodzie.
- Pogoda ma wpływ na prędkość, ale nie na krzywiznę drogi (zaznaczone w kodzie jako TODO).
- Światła są uproszczone (równy cykl dla każdego wjazdu, bez faz kolizyjnych).
- Brak testów automatycznych.
