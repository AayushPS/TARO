import { renderHook, act } from "@testing-library/react";
import { useEventLog } from "@shared/hooks/useEventLog";

describe("useEventLog", () => {
  it("keeps only the newest entries up to capacity", () => {
    const { result } = renderHook(() => useEventLog(2));

    act(() => {
      result.current.append({ message: "first" });
      result.current.append({ message: "second" });
      result.current.append({ message: "third" });
    });

    expect(result.current.entries).toHaveLength(2);
    expect(result.current.entries[0].message).toBe("second");
    expect(result.current.entries[1].message).toBe("third");
  });
});
