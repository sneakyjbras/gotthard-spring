import { useCallback, useEffect, useState } from 'react';
import { applyThemePreference, nextThemePreference, readStoredThemePreference, type ThemePreference } from './theme';

export interface UseThemePreference {
  preference: ThemePreference;
  cyclePreference: () => void;
}

/** Applies and persists the operator's theme override; cycles system → light → dark → system. */
export function useThemePreference(): UseThemePreference {
  const [preference, setPreference] = useState<ThemePreference>(readStoredThemePreference);

  useEffect(() => {
    applyThemePreference(preference);
  }, [preference]);

  const cyclePreference = useCallback(() => {
    setPreference(nextThemePreference);
  }, []);

  return { preference, cyclePreference };
}
