package com.jellio.tv.data.model

import com.jellio.tv.data.prefs.AppLanguage
import com.jellio.tv.data.prefs.DisplayPreferences
import java.util.Calendar

@androidx.compose.runtime.Immutable
data class ShowsEditorial(val icon: String, val label: String, val tagline: String, val description: String)

@androidx.compose.runtime.Immutable
data class BucketCopy(val kicker: String, val title: String, val subtitle: String)

private val BUCKET_ICONS = mapOf(
    "morning" to "wb_sunny",
    "afternoon" to "wb_cloudy",
    "evening" to "weekend",
    "night" to "bedtime",
)

private val BUCKET_INDEX = mapOf("morning" to 0, "afternoon" to 1, "evening" to 2, "night" to 3)

private val BUCKET_VARIANTS_EN = mapOf(
    "morning" to listOf(
        BucketCopy("Morning Lineup", "Easing into series", "Slow-burn worlds and bright chapters worth opening with coffee."),
        BucketCopy("Good Morning", "Today's openers", "Shows to ease into while the day is still quiet."),
        BucketCopy("Daybreak", "First-light picks", "Worlds to step into before the inbox catches up."),
        BucketCopy("AM Picks", "Coffee-and-couch", "Half-hours, anthologies, and a few epics for the morning routine."),
        BucketCopy("Open the Day", "Shows with mileage", "Long-running comforts and new chapters worth pressing play on."),
        BucketCopy("Quiet Hours", "Slow-burn starts", "Stories that reward your attention before the day gets loud."),
        BucketCopy("This Morning", "Worth catching up on", "What everyone has been quietly binging this week."),
    ),
    "afternoon" to listOf(
        BucketCopy("Afternoon Picks", "Daytime watching", "Easy half-hours and lighter dramas to ride out the afternoon."),
        BucketCopy("Midday Lineup", "Between meetings", "Episodes you can drop into without losing the thread."),
        BucketCopy("Afternoon Roll", "Pick up an episode", "Lunch-break comedies and slow-cooker dramas, ready when you are."),
        BucketCopy("The Long Lunch", "Shows to disappear into", "Worlds wide enough for an hour or a whole free afternoon."),
        BucketCopy("Daylight Watching", "Bright-side series", "Sharp comedies, sunny worlds, and the occasional binge bait."),
        BucketCopy("Holdover Picks", "Carry it through the day", "Companion series for whatever the afternoon throws at you."),
        BucketCopy("PM Picks", "Couch hours", "Shows for the part of the day that runs on coffee and snacks."),
    ),
    "evening" to listOf(
        BucketCopy("Tonight", "Tonight's lineup", "Prestige drama, weekly chapters, and series worth disappearing into."),
        BucketCopy("Prime Time", "What to watch tonight", "Crowd-pleasers, prestige picks, and the kind of series people text about."),
        BucketCopy("Sundown", "Evening on the couch", "Drop-in chapters and long arcs for the post-dinner stretch."),
        BucketCopy("Press Play", "Tonight's marquee", "The series that make the rest of the night disappear."),
        BucketCopy("Tonight's Slate", "Episodes worth the evening", "What's hot this week, what's prestige forever, what's worth the hours."),
        BucketCopy("Showtime", "Tonight's main event", "Shows for the part of the day you actually look forward to."),
        BucketCopy("Saved for Now", "Tonight's binge bait", "Pilots that pull you in and finales that earn the season."),
    ),
    "night" to listOf(
        BucketCopy("Late Night", "After-hours picks", "Dark, immersive, and binge-worthy when the house is quiet."),
        BucketCopy("Past Midnight", "One more episode", "Shows for the part of the night that won't let you sleep."),
        BucketCopy("Witching Hour", "Late-night chapters", "Pull-you-under stories for the quietest part of the day."),
        BucketCopy("Lights Out", "Headphone series", "Slow, strange, and absorbing. Best with the lights down low."),
        BucketCopy("Insomnia Lineup", "Worth the lost hour", "Dense plots and rich worlds for when sleep is not happening."),
        BucketCopy("Late Show", "After the news", "Quiet dramas, sharp thrillers, and series you save for yourself."),
        BucketCopy("Night Owl", "While the world's asleep", "Shows with the patience to match your late-night hours."),
    ),
)

private val BUCKET_VARIANTS_DE = mapOf(
    "morning" to listOf(
        BucketCopy("Morgen-Lineup", "Sanfter Serienauftakt", "Ruhige Welten und heitere Kapitel, perfekt zum Kaffee."),
        BucketCopy("Guten Morgen", "Serien zum Wachwerden", "Serien zum Genießen, solange der Tag noch ruhig ist."),
        BucketCopy("Tagesanbruch", "Titel im ersten Morgenlicht", "Geschichten, in die man eintaucht, bevor der Alltag ruft."),
        BucketCopy("Morgen-Tipps", "Kaffee auf der Couch", "Halbstünder, Anthologien und kleine Epen für den Morgen."),
        BucketCopy("Tag beginnen", "Serien mit Ausdauer", "Vertraute Begleiter und neue Kapitel zum Weiterschauen."),
        BucketCopy("Ruhige Stunden", "Ruhig anfangen", "Geschichten, die Ihre Aufmerksamkeit belohnen."),
        BucketCopy("Heute Morgen", "Sehenswert nachholen", "Was in dieser Woche besonders gern geschaut wurde."),
    ),
    "afternoon" to listOf(
        BucketCopy("Nachmittags-Tipps", "Tagesbegleiter", "Leichte Comedys und entspannte Dramen für den Nachmittag."),
        BucketCopy("Mittags-Lineup", "Zwischen den Terminen", "Episoden, in die man ohne Umschweife eintauchen kann."),
        BucketCopy("Nachmittagsfolge", "Zeit für eine Episode", "Pausenunterhaltung und Serien für zwischendurch."),
        BucketCopy("Ausgedehnte Pause", "Serien zum Abtauchen", "Große Geschichten für eine freie Stunde oder den ganzen Tag."),
        BucketCopy("Am Nachmittag", "Helle Serienwelten", "Witzige Comedys, sonnige Welten und packende Binge-Hits."),
        BucketCopy("Begleiter für den Tag", "Gut durch den Nachmittag", "Serien, die zu jedem Nachmittagstempo passen."),
        BucketCopy("Nachmittagsstunden", "Zeit auf dem Sofa", "Serien für die gemütlichste Zeit des Tages."),
    ),
    "evening" to listOf(
        BucketCopy("Heute Abend", "Das Abendprogramm", "Hochkarätige Dramen, wöchentliche Hits und packende Serien."),
        BucketCopy("Primetime", "Beste Abendunterhaltung", "Publikumslieblinge, Prestigetitel und Serien, über die man spricht."),
        BucketCopy("Sonnenuntergang", "Abend auf der Couch", "Lange Handlungsbögen für entspannte Stunden nach dem Essen."),
        BucketCopy("Auf Play drücken", "Der Haupttitel des Abends", "Serien, die den Feierabend wie im Flug vergehen lassen."),
        BucketCopy("Abend-Repertoire", "Episoden für den Abend", "Aktuelle Hits und Klassiker, die jede Minute wert sind."),
        BucketCopy("Showtime", "Der Höhepunkt des Tages", "Serien für die beste Zeit des Abends."),
        BucketCopy("Für heute aufgehoben", "Serienfutter für den Abend", "Piloten, die fesseln, und Staffelfinals, die begeistern."),
    ),
    "night" to listOf(
        BucketCopy("Spätabend", "Nachtschwärmer-Tipps", "Düster, packend und intensiv, wenn alles schläft."),
        BucketCopy("Nach Mitternacht", "Nur noch eine Folge", "Serien für die Stunden, die einen nicht schlafen lassen."),
        BucketCopy("Geisterstunde", "Kapitel zur späten Stunde", "Faszinierende Geschichten für die stillste Zeit der Nacht."),
        BucketCopy("Licht aus", "Kopfhörer-Serien", "Ruhig, mysteriös und fesselnd. Am besten im Dunkeln."),
        BucketCopy("Schlaflos", "Jede wache Stunde wert", "Dichte Handlungen und reiche Welten gegen das Wachbleiben."),
        BucketCopy("Spätprogramm", "Nach den Nachrichten", "Ruhige Dramen, clevere Thriller und persönliche Lieblinge."),
        BucketCopy("Nachteule", "Während die Welt schläft", "Serien, die genau zum Nachtrhythmus passen."),
    ),
)

private val BUCKET_VARIANTS_ES = mapOf(
    "morning" to listOf(
        BucketCopy("Selección matinal", "Empezando las series", "Mundos envolventes y capítulos brillantes para empezar con un café."),
        BucketCopy("Buenos días", "Aperturas de hoy", "Series para disfrutar mientras el día aún está tranquilo."),
        BucketCopy("Amanecer", "Primeras luces", "Mundos en los que sumergirse antes de empezar el día."),
        BucketCopy("Recomendaciones AM", "Café y sofá", "Media hora, antologías y capítulos perfectos para la mañana."),
        BucketCopy("Abre el día", "Series duraderas", "Clásicos reconfortantes y nuevos capítulos listos para reproducir."),
        BucketCopy("Horas de calma", "Inicios pausados", "Historias que merecen atención antes del ajetreo del día."),
        BucketCopy("Esta mañana", "Vale la pena ponerse al día", "Lo que todo el mundo ha estado viendo discretamente esta semana."),
    ),
    "afternoon" to listOf(
        BucketCopy("Para la tarde", "Viendo de día", "Medias horas ligeras y dramas suaves para pasar la tarde."),
        BucketCopy("Pausa de mediodía", "Entre reuniones", "Episodios a los que sumarse sin perder el hilo."),
        BucketCopy("Momento de serie", "Pon un episodio", "Comedias de almuerzo y dramas pausados a tu ritmo."),
        BucketCopy("Pausa larga", "Series para perderse", "Universos amplios para una hora o una tarde libre entera."),
        BucketCopy("Historias de día", "Series luminosas", "Comedias ágiles, mundos cálidos y maratones inevitables."),
        BucketCopy("Para continuar", "Acompaña el día", "Series compañeras para cualquier momento de la tarde."),
        BucketCopy("Tarde en el sofá", "Horas de descanso", "Series para la parte del día con café y aperitivos."),
    ),
    "evening" to listOf(
        BucketCopy("Esta noche", "Programación de hoy", "Dramas de prestigio, capítulos semanales y series inmersivas."),
        BucketCopy("Hora punta", "Qué ver esta noche", "Favoritos del público, joyas aclamadas y series comentadas."),
        BucketCopy("Puesta de sol", "Tarde-noche en el sofá", "Capítulos sueltos y tramas largas tras la cena."),
        BucketCopy("Dale al play", "La estrella de la noche", "Las series que hacen que la noche se pase volando."),
        BucketCopy("Cartelera nocturna", "Episodios para disfrutar", "Lo más visto de la semana y obras imprescindibles."),
        BucketCopy("Hora del espectáculo", "El plato fuerte", "Series para el momento del día que más esperabas."),
        BucketCopy("Guardado para hoy", "Maratón garantizado", "Pilotos que atrapan y finales que coronan la temporada."),
    ),
    "night" to listOf(
        BucketCopy("Madrugada", "Selección nocturna", "Oscuras, absorbentes y adictivas cuando la casa duerme."),
        BucketCopy("Pasada la medianoche", "Solo un episodio más", "Series para esa parte de la noche que no te deja dormir."),
        BucketCopy("Hora bruja", "Capítulos de madrugada", "Historias atrapantes para la hora más silenciosa."),
        BucketCopy("Luces fuera", "Para ver con auriculares", "Lentas, intrigantes y envolventes a media luz."),
        BucketCopy("Insomnio", "Vale la pena el desvelo", "Tramas densas y mundos ricos cuando el sueño se resiste."),
        BucketCopy("Sesión golfa", "Tras las noticias", "Dramas íntimos, thrillers ágiles y series para uno mismo."),
        BucketCopy("Búho nocturno", "Mientras el mundo duerme", "Series con la paciencia ideal para tus horas nocturnas."),
    ),
)

private val BUCKET_VARIANTS_FR = mapOf(
    "morning" to listOf(
        BucketCopy("Sélection matinale", "Réveil en séries", "Mondes immersifs et épisodes lumineux à savourer avec un café."),
        BucketCopy("Bonjour", "Les ouvertures du jour", "Des séries à découvrir tant que la maison est encore calme."),
        BucketCopy("Aube", "Premières lueurs", "Des univers dans lesquels s'évader avant le tourbillon de la journée."),
        BucketCopy("Sélections du matin", "Café et canapé", "Formats courts, anthologies et épopées pour la routine matinale."),
        BucketCopy("Ouvrir la journée", "Séries au long cours", "Doux repères et nouveaux chapitres à savourer."),
        BucketCopy("Heures paisibles", "Démarrage en douceur", "Des histoires qui méritent toute votre attention."),
        BucketCopy("Ce matin", "À rattraper absolument", "Ce que tout le monde regarde discrètement cette semaine."),
    ),
    "afternoon" to listOf(
        BucketCopy("Pour l'après-midi", "Visionnage de jour", "Formats courts et drames légers pour rythmer l'après-midi."),
        BucketCopy("Pause de midi", "Entre deux rendez-vous", "Des épisodes parfaits sans perdre le fil de l'histoire."),
        BucketCopy("Pause série", "Lancer un épisode", "Comédies de déjeuner et intrigues captivantes."),
        BucketCopy("Le grand break", "Des séries où plonger", "Des univers assez vastes pour une heure ou une après-midi."),
        BucketCopy("En plein jour", "Séries rayonnantes", "Comédies vives, univers ensoleillés et coups de cœur."),
        BucketCopy("Au fil des heures", "Tout au long du jour", "Des séries fidèles compagnes de votre après-midi."),
        BucketCopy("Heures canapé", "Détente assurée", "Les séries idéales pour la pause café et collation."),
    ),
    "evening" to listOf(
        BucketCopy("Ce soir", "La soirée séries", "Grands drames, épisodes hebdomadaires et univers marquants."),
        BucketCopy("Prime time", "À voir ce soir", "Succès populaires, choix prestigieux et séries incontournables."),
        BucketCopy("Coucher de soleil", "Soirée sur le canapé", "Épisodes poignants et intrigues fortes après dîner."),
        BucketCopy("Appuyez sur play", "L'affiche du soir", "Les séries qui font oublier l'heure."),
        BucketCopy("Au programme", "Des épisodes précieux", "Ce qui fait sensation et les classiques indémodables."),
        BucketCopy("L'heure du show", "Le rendez-vous du soir", "Les séries pour le moment que vous attendiez le plus."),
        BucketCopy("Gardé pour ce soir", "Marathon en vue", "Des pilotes captivants et des fins de saison grandioses."),
    ),
    "night" to listOf(
        BucketCopy("Fin de soirée", "Sélection tardive", "Sombres, captivantes et intenses quand la maison s'endort."),
        BucketCopy("Passé minuit", "Encore un épisode", "Des séries pour la nuit qui refuse de vous laisser dormir."),
        BucketCopy("L'heure sombre", "Épisodes nocturnes", "Des récits hypnotiques pour le moment le plus calme du jour."),
        BucketCopy("Lumières éteintes", "Au casque", "Lentes, envoûtantes et mystérieuses dans la pénombre."),
        BucketCopy("Insomnie", "Une heure blanche méritée", "Intrigues riches et mondes denses quand le sommeil s'enfuit."),
        BucketCopy("Séance de nuit", "Après le journal", "Drames feutrés, thrillers acérés et séries intimes."),
        BucketCopy("Oiseau de nuit", "Tandis que le monde dort", "Des séries qui épousent le rythme de vos veilles."),
    ),
)

private val BUCKET_VARIANTS_IT = mapOf(
    "morning" to listOf(
        BucketCopy("Programma del mattino", "Risveglio con le serie", "Mondi immersivi e capitoli luminosi da gustare con un caffè."),
        BucketCopy("Buongiorno", "I titoli di oggi", "Serie con cui iniziare mentre la giornata è ancora tranquilla."),
        BucketCopy("Alba", "Prime luci", "Universi in cui immergersi prima degli impegni quotidiani."),
        BucketCopy("Scelte del mattino", "Caffè e divano", "Mezz'ore, antologie e grandi storie per la routine mattutina."),
        BucketCopy("Inizia la giornata", "Serie di lungo corso", "Titoli rassicuranti e nuovi episodi da far partire."),
        BucketCopy("Ore tranquille", "Inizi lenti", "Storie che premiano la tua attenzione prima del trambusto."),
        BucketCopy("Stamattina", "Da recuperare", "Cosa hanno guardato tutti con passione questa settimana."),
    ),
    "afternoon" to listOf(
        BucketCopy("Scelte del pomeriggio", "Visione diurna", "Episodi leggeri e commedie per accompagnare il pomeriggio."),
        BucketCopy("Pausa di metà giornata", "Tra un impegno e l'altro", "Episodi da seguire facilmente senza perdere il filo."),
        BucketCopy("Pausa serie", "Scegli un episodio", "Commedie veloci e drammi coinvolgenti pronti per te."),
        BucketCopy("La pausa lunga", "Serie in cui perdersi", "Mondi ampi per un'ora o un intero pomeriggio libero."),
        BucketCopy("Luce del giorno", "Serie brillanti", "Commedie brillanti, atmosfere solari e grandi storie."),
        BucketCopy("Da continuare", "Per tutto il giorno", "Serie fidate per qualunque cosa riservi il pomeriggio."),
        BucketCopy("Ore sul divano", "Relax pomeridiano", "Serie perfette per la pausa con snack e caffè."),
    ),
    "evening" to listOf(
        BucketCopy("Stasera", "Il programma di stasera", "Grandi drammi, appuntamenti settimanali e serie imperdibili."),
        BucketCopy("Prima serata", "Cosa guardare stasera", "I preferiti dal pubblico, grandi produzioni e serie di culto."),
        BucketCopy("Tramonto", "Serata sul divano", "Grandi capitoli e trame avvincenti per il dopocena."),
        BucketCopy("Premi play", "L'evento della serata", "Le serie che fanno volare via la notte."),
        BucketCopy("La scaletta di stasera", "Episodi imperdibili", "I successi della settimana e i titoli più acclamati."),
        BucketCopy("Showtime", "L'evento principale", "Le serie per il momento migliore della giornata."),
        BucketCopy("Tenuto da parte", "Maratona serale", "Episodi pilota travolgenti e finali memorabili."),
    ),
    "night" to listOf(
        BucketCopy("Notte fonda", "Visioni a tarda notte", "Intriganti, oscure e avvincenti quando la casa è silenziosa."),
        BucketCopy("Dopo mezzanotte", "Ancora un episodio", "Serie per quelle notti in cui il sonno non arriva."),
        BucketCopy("L'ora delle streghe", "Capitoli notturni", "Storie che ti catturano nel momento più quieto."),
        BucketCopy("A luci spente", "Da ascoltare in cuffia", "Lente, insolite e avvolgenti a luci basse."),
        BucketCopy("Insonnia", "Un'ora persa con piacere", "Trame fitte e mondi ricchi quando non si dorme."),
        BucketCopy("Seconda serata", "Dopo il notiziario", "Drammi intimi, thriller tesi e serie per te stesso."),
        BucketCopy("Nottambulo", "Mentre il mondo dorme", "Serie con la pazienza giusta per le ore piccole."),
    ),
)

private val BUCKET_VARIANTS_PL = mapOf(
    "morning" to listOf(
        BucketCopy("Poranny repertuar", "Spokojny start w seriale", "Wciągające światy i jasne odcinki idealne do porannej kawy."),
        BucketCopy("Dzień dobry", "Na dobry początek", "Seriale na spokojny poranek, zanim dzień nabierze tempa."),
        BucketCopy("Świt", "Seanse o poranku", "Światy, w które warto wejść, nim odezwie się skrzynka odbiorcza."),
        BucketCopy("Poranny wybór", "Kawa i kanapa", "Półgodzinne odcinki, antologie i wielkie historie na rano."),
        BucketCopy("Otwórz dzień", "Seriale na dłużej", "Ulubione i nowe odcinki warte włączenia już teraz."),
        BucketCopy("Ciche godziny", "Spokojny początek", "Opowieści, które nagradzają uwagę, nim zrobi się głośno."),
        BucketCopy("Dziś rano", "Warte nadrobienia", "To, co wszyscy po cichu oglądali w tym tygodniu."),
    ),
    "afternoon" to listOf(
        BucketCopy("Na popołudnie", "Seanse za dnia", "Lekkie komedie i przyjemne dramaty na popołudniowe godziny."),
        BucketCopy("W środku dnia", "Pomiędzy zajęciami", "Odcinki, w które łatwo wejść bez utraty wątku."),
        BucketCopy("Popołudniowy odcinek", "Włącz coś teraz", "Komedie na przerwę i wciągające historie gotowe na seans."),
        BucketCopy("Dłuższy odpoczynek", "Seriale, w których przepadasz", "Światy dość obszerne na wolną godzinę lub całe popołudnie."),
        BucketCopy("W świetle dnia", "Pogodne seriale", "Błyskotliwe komedie, ciepłe światy i wciągające hity."),
        BucketCopy("Na resztę dnia", "Dobre towarzystwo", "Seriale, które świetnie pasują do każdego popołudnia."),
        BucketCopy("Chwila na kanapie", "Czas na odpoczynek", "Seriale na tę część dnia, która upływa przy kawie i przekąskach."),
    ),
    "evening" to listOf(
        BucketCopy("Dziś wieczorem", "Wieczorny repertuar", "Wielkie dramaty, cotygodniowe odcinki i wciągające historie."),
        BucketCopy("Najlepszy czas", "Co obejrzeć dziś wieczorem", "Ulubieńcy widzów, nagradzane produkcje i głośne seriale."),
        BucketCopy("O zmierzchu", "Wieczór na kanapie", "Pojedyncze odcinki i długie wątki na odpoczynek po kolacji."),
        BucketCopy("Wciśnij odtwórz", "Główny seans wieczoru", "Seriale, przy których reszta nocy po prostu znika."),
        BucketCopy("Wieczorna ramówka", "Odcinki warte wieczoru", "Największe hity tygodnia i ponadczasowe klasyki."),
        BucketCopy("Czas na seans", "Wydarzenie wieczoru", "Seriale na tę porę dnia, na którą czeka się najbardziej."),
        BucketCopy("Zostawione na dziś", "Idealne do maratonu", "Pilotowe odcinki, które chwytają od razu, i mocne finały."),
    ),
    "night" to listOf(
        BucketCopy("Późna noc", "Nocne seanse", "Mroczne, wciągające i magnetyczne, gdy dom już śpi."),
        BucketCopy("Po północy", "Jeszcze jeden odcinek", "Seriale na tę część nocy, która nie pozwala zasnąć."),
        BucketCopy("Godzina duchów", "Nocne opowieści", "Historie, które wciągają bez reszty w najcichszej porze dnia."),
        BucketCopy("Światła zgaszone", "W słuchawkach", "Niespieszne, intrygujące i pochłaniające przy przygaszonym świetle."),
        BucketCopy("Gdy sen nie przychodzi", "Warci straconej godziny", "Gęste fabuły i bogate światy, gdy sen nie nadchodzi."),
        BucketCopy("Nocny seans", "Po wiadomościach", "Kameralne dramaty, ostre thrillery i seriale tylko dla siebie."),
        BucketCopy("Nocny marek", "Gdy świat śpi", "Seriale mające cierpliwość idealną na późne godziny."),
    ),
)

private val BUCKET_VARIANTS_PT = mapOf(
    "morning" to listOf(
        BucketCopy("Seleção matinal", "Começando as séries", "Mundos envolventes e capítulos leves para saborear com café."),
        BucketCopy("Bom dia", "As estreias de hoje", "Séries para relaxar enquanto o dia ainda está tranquilo."),
        BucketCopy("Amanhecer", "Primeira luz", "Mundos onde entrar antes que a rotina comece."),
        BucketCopy("Escolhas da manhã", "Café e sofá", "Episódios curtos, antologias e grandes histórias para a manhã."),
        BucketCopy("Abra o dia", "Séries duradouras", "Conforto clássico e novos capítulos prontos para reproduzir."),
        BucketCopy("Horas de calma", "Inícios lentos", "Histórias que recompensam a sua atenção antes do barulho do dia."),
        BucketCopy("Esta manhã", "Vale a pena acompanhar", "O que todos andam a maratonar com calma esta semana."),
    ),
    "afternoon" to listOf(
        BucketCopy("Para a tarde", "Sessão diurna", "Episódios leves e dramas tranquilos para passar a tarde."),
        BucketCopy("Pausa do meio-dia", "Entre compromissos", "Episódios para acompanhar facilmente sem perder o fio."),
        BucketCopy("Hora do episódio", "Escolha um capítulo", "Comédias rápidas e dramas envolventes prontos para ver."),
        BucketCopy("Pausa prolongada", "Séries para mergulhar", "Universos amplos para uma hora ou uma tarde inteira."),
        BucketCopy("À luz do dia", "Séries luminosas", "Comédias espirituosas, ambientes calorosos e maratonas garantidas."),
        BucketCopy("Para continuar", "Ao longo do dia", "Séries ideais para acompanhar o ritmo da sua tarde."),
        BucketCopy("Horas no sofá", "Momento de descanso", "Séries para a parte do dia acompanhada de café e lanche."),
    ),
    "evening" to listOf(
        BucketCopy("Esta noite", "Programação da noite", "Grandes dramas, episódios semanais e séries imperdíveis."),
        BucketCopy("Horário nobre", "O que ver esta noite", "Os favoritos do público, escolhas de prestígio e sucessos."),
        BucketCopy("Pôr do sol", "Noite no sofá", "Episódios envolventes e arcos longos para depois do jantar."),
        BucketCopy("Carregue no play", "O destaque da noite", "As séries que fazem a noite passar num instante."),
        BucketCopy("Cartaz de hoje", "Episódios que valem a pena", "Os maiores destaques da semana e clássicos aclamados."),
        BucketCopy("Hora do espetáculo", "O evento principal", "Séries para a melhor parte do seu dia."),
        BucketCopy("Guardado para hoje", "Para maratonar", "Pilotos que conquistam e finais de temporada marcantes."),
    ),
    "night" to listOf(
        BucketCopy("Fim de noite", "Escolhas da madrugada", "Sombrias, envolventes e intensas quando tudo silencia."),
        BucketCopy("Passa da meia-noite", "Só mais um episódio", "Séries para aquela parte da noite em que o sono não vem."),
        BucketCopy("Hora das bruxas", "Capítulos pela noite dentro", "Histórias hipnóticas para o momento mais sereno."),
        BucketCopy("Luzes apagadas", "Com auscultadores", "Lentas, intrigantes e imersivas à meia-luz."),
        BucketCopy("Insónia", "Vale a hora perdida", "Enredos densos e mundos ricos quando não se consegue dormir."),
        BucketCopy("Sessão da noite", "Depois do noticiário", "Dramas intimistas, thrillers tensos e séries só para si."),
        BucketCopy("Coruja da noite", "Enquanto o mundo dorme", "Séries com a paciência perfeita para as horas tardias."),
    ),
)

private fun dayBucket(hour: Int): String {
    val h = if (hour < 5) hour + 24 else hour
    return when {
        h in 5..11 -> "morning"
        h in 12..16 -> "afternoon"
        h in 17..21 -> "evening"
        else -> "night"
    }
}

private fun dayOfYear(): Int = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

private fun bucketVariantsFor(lang: AppLanguage): Map<String, List<BucketCopy>> = when (lang) {
    AppLanguage.ENGLISH -> BUCKET_VARIANTS_EN
    AppLanguage.GERMAN -> BUCKET_VARIANTS_DE
    AppLanguage.SPANISH -> BUCKET_VARIANTS_ES
    AppLanguage.FRENCH -> BUCKET_VARIANTS_FR
    AppLanguage.ITALIAN -> BUCKET_VARIANTS_IT
    AppLanguage.POLISH -> BUCKET_VARIANTS_PL
    AppLanguage.PORTUGUESE -> BUCKET_VARIANTS_PT
}

fun pickTimeOfDayCopy(
    hour: Int,
    lang: AppLanguage = DisplayPreferences.displayLanguage,
): BucketCopy {
    val bucket = dayBucket(hour)
    val variants = bucketVariantsFor(lang).getValue(bucket)
    val idx = (dayOfYear() + (BUCKET_INDEX.getValue(bucket) * 3)) % variants.size
    return variants[idx]
}

fun showsEditorial(hour: Int): ShowsEditorial {
    val bucket = dayBucket(hour)
    val copy = pickTimeOfDayCopy(hour)
    return ShowsEditorial(
        icon = BUCKET_ICONS.getValue(bucket),
        label = copy.kicker,
        tagline = copy.title,
        description = copy.subtitle,
    )
}

@androidx.compose.runtime.Immutable
data class TimeOfDayRow(val title: String, val genres: List<String>, val keywords: List<String> = emptyList())

private fun rotate(variants: Map<String, List<TimeOfDayRow>>, hour: Int): TimeOfDayRow {
    val bucket = dayBucket(hour)
    val list = variants.getValue(bucket)
    return list[(dayOfYear() + BUCKET_INDEX.getValue(bucket) * 3) % list.size]
}

private val MOVIE_TITLES: Map<String, Map<String, List<String>>> = mapOf(
    "en" to mapOf(
        "morning" to listOf("Easy morning movies", "Feel-good features", "Documentaries with your coffee", "Sunny side up"),
        "afternoon" to listOf("Afternoon blockbusters", "Comfort classics", "Quests and adventures", "Matinee westerns and wars"),
        "evening" to listOf("Tonight's feature", "Date night", "Edge of your seat", "Epic sci-fi and fantasy"),
        "night" to listOf("After-dark horror", "Crime after midnight", "Strange late-night picks", "Midnight mysteries"),
    ),
    "de" to mapOf(
        "morning" to listOf("Leichte Morgenfilme", "Wohlfühlfilme", "Dokumentationen zum Kaffee", "Sonniger Morgen"),
        "afternoon" to listOf("Nachmittags-Blockbuster", "Wohlfühlklassiker", "Reisen und Abenteuer", "Matinee-Western und Kriegsfilme"),
        "evening" to listOf("Hauptfilm des Abends", "Filmabend zu zweit", "Nervenkitzel pur", "Epische Science-Fiction und Fantasy"),
        "night" to listOf("Late-Night-Horror", "Krimi nach Mitternacht", "Kuriose Spätvorstellungen", "Mitternachtsgeheimnisse"),
    ),
    "es" to mapOf(
        "morning" to listOf("Películas ligeras matinales", "Historias para sentirse bien", "Documentales con el café", "Empezando el día"),
        "afternoon" to listOf("Éxitos de la tarde", "Clásicos reconfortantes", "Búsquedas y aventuras", "Matiné del oeste y bélico"),
        "evening" to listOf("La película de esta noche", "Noche de cita", "Al borde del asiento", "Ciencia ficción y fantasía épica"),
        "night" to listOf("Terror nocturno", "Crimen pasada la medianoche", "Rarezas de trasnoche", "Misterios de medianoche"),
    ),
    "fr" to mapOf(
        "morning" to listOf("Films doux du matin", "Pépites réconfortantes", "Documentaires avec le café", "Un matin ensoleillé"),
        "afternoon" to listOf("Blockbusters de l'après-midi", "Classiques réconfortants", "Quêtes et aventures", "Matinée western et guerre"),
        "evening" to listOf("Le film de la soirée", "Soirée romantique", "Frissons garantis", "Épopées SF et fantasy"),
        "night" to listOf("Horreur nocturne", "Polar après minuit", "Pépites étranges de la nuit", "Mystères de minuit"),
    ),
    "it" to mapOf(
        "morning" to listOf("Film leggeri per il mattino", "Storie che scaldano il cuore", "Documentari con il caffè", "Inizia la giornata"),
        "afternoon" to listOf("Blockbuster del pomeriggio", "Classici rassicuranti", "Missioni e avventure", "Matinée western e bellici"),
        "evening" to listOf("Il film di stasera", "Serata romantica", "Col fiato sospeso", "Epica fantascienza e fantasy"),
        "night" to listOf("Horror a notte fonda", "Crimine dopo mezzanotte", "Scelte insolite a tarda notte", "Misteri di mezzanotte"),
    ),
    "pl" to mapOf(
        "morning" to listOf("Lekkie filmy na poranek", "Ciepłe historie", "Dokumenty do kawy", "Słoneczny poranek"),
        "afternoon" to listOf("Popołudniowe hity", "Sprawdzone klasyki", "Wyprawy i przygody", "Popołudniowe westerny i wojenne"),
        "evening" to listOf("Hit dzisiejszego wieczoru", "Wieczór we dwoje", "W napięciu", "Epickie sci-fi i fantasy"),
        "night" to listOf("Nocne horrory", "Kryminał po północy", "Osobliwe nocne seanse", "Zagadki o północy"),
    ),
    "pt" to mapOf(
        "morning" to listOf("Filmes leves para a manhã", "Histórias reconfortantes", "Documentários com o café", "Manhã ensolarada"),
        "afternoon" to listOf("Sucessos da tarde", "Clássicos reconfortantes", "Buscas e aventuras", "Matinê de faroeste e guerra"),
        "evening" to listOf("O filme desta noite", "Noite a dois", "Na ponta da cadeira", "Ficção científica e fantasia épicas"),
        "night" to listOf("Terror noturno", "Crime depois da meia-noite", "Escolhas incomuns da madrugada", "Mistérios da meia-noite"),
    ),
)

private val ANIME_TITLES: Map<String, Map<String, List<String>>> = mapOf(
    "en" to mapOf(
        "morning" to listOf("Slice-of-life sunrise", "School-day openers", "Morning training arcs", "Cozy and wholesome"),
        "afternoon" to listOf("After-school adventures", "Battle shōnen break", "Club room picks", "Gag anime for the break"),
        "evening" to listOf("Tonight's big arcs", "Pilots, to your units", "Romance after dark", "Swords and sorcery"),
        "night" to listOf("Isekai: diving into another world", "Supernatural after midnight", "Psychological late-night picks", "Neon and cyberpunk"),
    ),
    "de" to mapOf(
        "morning" to listOf("Slice-of-Life am Morgen", "Schultag-Auftakt", "Morgendliche Trainings-Arcs", "Gemütlich und herzerwärmend"),
        "afternoon" to listOf("Abenteuer nach der Schule", "Battle-Shōnen-Pause", "Klubraum-Favoriten", "Comedy-Anime für zwischendurch"),
        "evening" to listOf("Die großen Arcs des Abends", "Piloten, in die Einheiten", "Romantik am Abend", "Schwerter und Zauberei"),
        "night" to listOf("Isekai: Ab in eine andere Welt", "Übernatürliches nach Mitternacht", "Psychologische Spättitel", "Neon und Cyberpunk"),
    ),
    "es" to mapOf(
        "morning" to listOf("Slice-of-life al amanecer", "Aperturas de día escolar", "Entrenamientos matutinos", "Acogedor y entrañable"),
        "afternoon" to listOf("Aventuras tras la escuela", "Pausa de battle shōnen", "Historias del club escolar", "Anime de comedia para el recreo"),
        "evening" to listOf("Los grandes arcos de esta noche", "Pilotos, a sus unidades", "Romance tras el anochecer", "Espadas y hechicería"),
        "night" to listOf("Isekai: directo a otro mundo", "Sobrenatural tras la medianoche", "Selección psicológica nocturna", "Neón y ciberpunk"),
    ),
    "fr" to mapOf(
        "morning" to listOf("Tranche de vie au lever du soleil", "Début de journée de cours", "Entraînements matinaux", "Doux et chaleureux"),
        "afternoon" to listOf("Aventures après les cours", "Pause shōnen de combat", "Sélection du club", "Animes comiques pour la pause"),
        "evening" to listOf("Les grands arcs de la soirée", "Pilotes, à vos unités", "Romance nocturne", "Épées et sorcellerie"),
        "night" to listOf("Isekai: plongée dans un autre monde", "Surnaturel après minuit", "Sélection psychologique tardive", "Néon et cyberpunk"),
    ),
    "it" to mapOf(
        "morning" to listOf("Slice-of-life all'alba", "Inizio della giornata scolastica", "Allenamenti mattutini", "Caldo e rassicurante"),
        "afternoon" to listOf("Avventure doposcuola", "Pausa battle shōnen", "Storie dal club", "Anime comici per la pausa"),
        "evening" to listOf("I grandi archi di stasera", "Piloti, alle vostre unità", "Romanticismo serale", "Spada e stregoneria"),
        "night" to listOf("Isekai: tuffo in un altro mondo", "Soprannaturale dopo mezzanotte", "Scelte psicologiche notturne", "Neon e cyberpunk"),
    ),
    "pl" to mapOf(
        "morning" to listOf("Okruchy życia o świcie", "Początek dnia w szkole", "Poranne treningi", "Ciepłe i przytulne"),
        "afternoon" to listOf("Przygody po lekcjach", "Przerwa na battle shōnen", "Klubowe opowieści", "Gagi anime na przerwę"),
        "evening" to listOf("Wielkie wątki dzisiejszego wieczoru", "Piloci, do jednostek", "Romantyczny wieczór", "Miecz i magia"),
        "night" to listOf("Isekai: podróż do innego świata", "Zjawiska nadprzyrodzone po północy", "Nocne kino psychologiczne", "Neon i cyberpunk"),
    ),
    "pt" to mapOf(
        "morning" to listOf("Slice-of-life ao amanhecer", "Aberturas de dia escolar", "Treinos matinais", "Aconchegante e afetuoso"),
        "afternoon" to listOf("Aventuras depois da escola", "Pausa com battle shōnen", "Histórias do clube", "Animes de comédia para a pausa"),
        "evening" to listOf("Os grandes arcos desta noite", "Pilotos, às suas unidades", "Romance ao anoitecer", "Espadas e feitiçaria"),
        "night" to listOf("Isekai: mergulho em outro mundo", "Sobrenatural depois da meia-noite", "Escolhas psicológicas da madrugada", "Neon e cyberpunk"),
    ),
)

private val MOVIE_BASE_ROWS = mapOf(
    "morning" to listOf(
        TimeOfDayRow("", listOf("Family", "Animation", "Comedy")),
        TimeOfDayRow("", listOf("Comedy", "Romance", "Family")),
        TimeOfDayRow("", listOf("Documentary", "History")),
        TimeOfDayRow("", listOf("Comedy", "Music", "Animation")),
    ),
    "afternoon" to listOf(
        TimeOfDayRow("", listOf("Action", "Adventure")),
        TimeOfDayRow("", listOf("Comedy", "Drama", "Romance")),
        TimeOfDayRow("", listOf("Adventure", "Fantasy", "Family")),
        TimeOfDayRow("", listOf("Western", "War", "History")),
    ),
    "evening" to listOf(
        TimeOfDayRow("", listOf("Drama", "Thriller", "Action")),
        TimeOfDayRow("", listOf("Romance", "Comedy")),
        TimeOfDayRow("", listOf("Thriller", "Crime", "Mystery")),
        TimeOfDayRow("", listOf("Science Fiction", "Fantasy")),
    ),
    "night" to listOf(
        TimeOfDayRow("", listOf("Horror", "Thriller")),
        TimeOfDayRow("", listOf("Crime", "Mystery", "Thriller")),
        TimeOfDayRow("", listOf("Science Fiction", "Mystery", "Fantasy")),
        TimeOfDayRow("", listOf("Mystery", "Thriller", "Horror")),
    ),
)

private val ANIME_BASE_ROWS = mapOf(
    "morning" to listOf(
        TimeOfDayRow("", listOf("Slice of Life", "Comedy"), listOf("school", "café", "cafe", "everyday")),
        TimeOfDayRow("", listOf("Slice of Life", "Comedy", "Romance"), listOf("high school", "classmate", "club")),
        TimeOfDayRow("", listOf("Sports", "Action"), listOf("tournament", "training", "team", "championship")),
        TimeOfDayRow("", listOf("Slice of Life", "Family", "Kids"), listOf("cooking", "food", "family", "countryside")),
    ),
    "afternoon" to listOf(
        TimeOfDayRow("", listOf("Adventure", "Comedy", "Action & Adventure"), listOf("journey", "adventure", "friends")),
        TimeOfDayRow("", listOf("Action", "Adventure", "Action & Adventure"), listOf("hero", "power", "battle", "strongest")),
        TimeOfDayRow("", listOf("Sports", "Music", "Slice of Life"), listOf("club", "band", "idol", "team")),
        TimeOfDayRow("", listOf("Comedy"), listOf("comedy", "gag", "hilarious")),
    ),
    "evening" to listOf(
        TimeOfDayRow("", listOf("Action", "Drama", "Fantasy", "Sci-Fi & Fantasy"), listOf("war", "destiny", "kingdom")),
        TimeOfDayRow("", listOf("Mecha", "Sci-Fi", "Sci-Fi & Fantasy"), listOf("mecha", "robot", "pilot", "giant")),
        TimeOfDayRow("", listOf("Romance", "Drama"), listOf("love", "confess", "relationship")),
        TimeOfDayRow("", listOf("Fantasy", "Adventure", "Sci-Fi & Fantasy"), listOf("magic", "sword", "demon lord", "dragon")),
    ),
    "night" to listOf(
        TimeOfDayRow("", listOf("Fantasy", "Sci-Fi & Fantasy"), listOf("isekai", "another world", "reincarnat", "transported", "summoned", "different world")),
        TimeOfDayRow("", listOf("Supernatural", "Horror", "Mystery"), listOf("ghost", "spirit", "curse", "yokai", "demon")),
        TimeOfDayRow("", listOf("Psychological", "Thriller", "Mystery"), listOf("mind", "death game", "detective", "survival")),
        TimeOfDayRow("", listOf("Sci-Fi", "Mecha", "Sci-Fi & Fantasy"), listOf("cyber", "future", "dystopia", "android", "virtual")),
    ),
)

private fun buildLocalizedRows(
    titlesByLang: Map<String, Map<String, List<String>>>,
    baseRows: Map<String, List<TimeOfDayRow>>,
    lang: AppLanguage,
): Map<String, List<TimeOfDayRow>> {
    val langCode = lang.code
    val titlesForLang = titlesByLang[langCode] ?: titlesByLang.getValue("en")
    val enTitles = titlesByLang.getValue("en")
    return baseRows.mapValues { (bucket, rows) ->
        val bucketTitles = titlesForLang[bucket] ?: enTitles.getValue(bucket)
        rows.mapIndexed { index, row ->
            row.copy(title = bucketTitles.getOrElse(index) { enTitles.getValue(bucket)[index] })
        }
    }
}

fun movieVariants(lang: AppLanguage = DisplayPreferences.displayLanguage): Map<String, List<TimeOfDayRow>> =
    buildLocalizedRows(MOVIE_TITLES, MOVIE_BASE_ROWS, lang)

fun animeVariants(lang: AppLanguage = DisplayPreferences.displayLanguage): Map<String, List<TimeOfDayRow>> =
    buildLocalizedRows(ANIME_TITLES, ANIME_BASE_ROWS, lang)

fun pickMovieRows(hour: Int, lang: AppLanguage = DisplayPreferences.displayLanguage): TimeOfDayRow =
    rotate(movieVariants(lang), hour)

fun pickAnimeRows(hour: Int, lang: AppLanguage = DisplayPreferences.displayLanguage): TimeOfDayRow =
    rotate(animeVariants(lang), hour)

private val SHOW_GENRES = mapOf(
    "morning" to listOf("Comedy", "Family", "Animation", "Documentary"),
    "afternoon" to listOf("Comedy", "Reality", "Action & Adventure", "Animation"),
    "evening" to listOf("Drama", "Crime", "Mystery", "Sci-Fi & Fantasy", "War & Politics"),
    "night" to listOf("Mystery", "Crime", "Sci-Fi & Fantasy", "Drama"),
)

fun moviesTimeOfDay(hour: Int): TimeOfDayRow = pickMovieRows(hour)

fun animeTimeOfDay(hour: Int): TimeOfDayRow = pickAnimeRows(hour)

// Shows keep their own copy (showsEditorial above); this is what fills it.
fun showsTimeOfDay(hour: Int): TimeOfDayRow =
    TimeOfDayRow(showsEditorial(hour).tagline, SHOW_GENRES.getValue(dayBucket(hour)))

// How well an anime matches a row: a keyword hit counts double a genre hit.
fun TimeOfDayRow.score(item: BaseItemDto): Int {
    val genreHits = item.Genres.orEmpty().count { genre -> genres.any { it.equals(genre, ignoreCase = true) } }
    val text = ((item.Name ?: "") + " " + (item.Overview ?: "")).lowercase()
    val keywordHits = keywords.count { text.contains(it.lowercase()) }
    return genreHits + keywordHits * 2
}
