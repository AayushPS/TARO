class IDMapper:
    """
    A builder-helper class to maintain Client ID (str) <-> Engine ID (int) mappings.
    Designed for the construction phase before data is passed to the immutable Java layer.
    """

    def __init__(self):
        # Forward mapping: String -> Int
        self._str_to_int: dict[str, int] = {}
        # Reverse mapping: Int -> String
        self._int_to_str: list[str] = []

    def get_or_create(self, external_id: str) -> int:
        """
        Gets the existing internal ID for a string, or creates a new one
        if it doesn't exist.
        """
        if external_id in self._str_to_int:
            return self._str_to_int[external_id]

        # Assign new ID
        new_id = len(self._int_to_str)
        self._str_to_int[external_id] = new_id
        self._int_to_str.append(external_id)

        return new_id

    def to_internal(self, external_id: str) -> int:
        """
        Look up internal ID. Raises KeyError if not found.
        """
        return self._str_to_int[external_id]

    def to_external(self, internal_id: int) -> str:
        """
        Look up external ID. Raises IndexError if not found.
        """
        # Python lists allow negative indexing (wrapping),
        # so we must explicitly block negative IDs to match the Java/Strict logic.
        if internal_id < 0:
            raise IndexError(f"Internal ID cannot be negative: {internal_id}")

        return self._int_to_str[internal_id]

    def contains_external(self, external_id: str) -> bool:
        return external_id in self._str_to_int

    def contains_internal(self, internal_id: int) -> bool:
        return 0 <= internal_id < len(self._int_to_str)

    def size(self) -> int:
        return len(self._int_to_str)

    def export_mappings(self) -> dict[str, int]:
        """
        Returns a copy of the mappings, suitable for passing to the
        Java IDMapper.createImmutable() factory.
        """
        return self._str_to_int.copy()