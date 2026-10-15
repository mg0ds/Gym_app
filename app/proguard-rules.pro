# Reguły R8 (ProGuard). Włączane razem z minifikacją w M9.
# Hilt, Room i Compose dostarczają własne reguły w bibliotekach (consumer rules).
#
# Zasady na przyszłość:
# - nie obfuskuj modeli danych serializowanych do JSON (backup w M9),
# - zachowaj nazwy klas encji Room tylko jeśli korzystasz z refleksji (nie korzystamy).
