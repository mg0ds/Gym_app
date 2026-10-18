# Reguły R8. Włączane razem z `isMinifyEnabled` w buildzie release (M9).
#
# Hilt, Room, Compose, Media3, Coil i DataStore dowożą własne reguły w swoich
# bibliotekach (consumer rules), więc nie powtarzamy ich tutaj. Zostaje jedno
# miejsce, w którym R8 faktycznie może coś zgubić: **serializacja JSON**
# (kotlinx.serialization) - używana przez kopię zapasową i import katalogu.

# --- kotlinx.serialization -------------------------------------------------
# Serializery generuje wtyczka kompilatora, ale R8 ich nie widzi: są tworzone
# przez `serializer()` i `$$serializer`, więc bez tych reguł optymalizator może
# je uznać za nieużywane i wyciąć. Efekt byłby cichy: build działa, a eksport
# kopii wywala się dopiero na urządzeniu.
-keepattributes *Annotation*, InnerClasses, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.**

# Serializery wbudowane (typy standardowe, listy, mapy).
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Nasze klasy @Serializable (encje Room w kopii zapasowej, modele kopii).
# Trzymamy cały `$$serializer` razem z jego deskryptorem: deskryptor zawiera
# nazwy pól, po których zapisujemy i czytamy JSON.
-keep,includedescriptorclasses class com.gymtracker.**$$serializer { *; }
-keepclassmembers class com.gymtracker.** {
    *** Companion;
}
-keepclasseswithmembers class com.gymtracker.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Room ------------------------------------------------------------------
# Wiersze zapytań zwracane do DAO (`data/db/rows`) i encje są tworzone przez
# generowany kod, więc R8 i tak wie, że są używane. Zostawiamy tylko to, czego
# Room szuka po nazwie: generowaną implementację bazy (lookup przez `Class.forName`).
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# --- Vico (wykresy karty postaci) ------------------------------------------
# Vico trzyma się publicznego API i Compose, ale jego modele osi bywają czytane
# po nazwach w czasie rysowania; bezpieczniej nie obfuskować pakietu biblioteki.
-keep class com.patrykandpatrick.vico.** { *; }
-dontwarn com.patrykandpatrick.vico.**
