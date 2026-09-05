/**
 * Theme is `data-theme` on `<html>`, exactly as tokens.css expects: absent
 * means "follow `prefers-color-scheme`", `"light"` / `"dark"` means the
 * operator overrode it explicitly. See `index.html` for the inline script
 * that applies the stored preference before first paint, avoiding a flash.
 */

export type ThemePreference = 'system' | 'light' | 'dark';

export const THEME_STORAGE_KEY = 'gotthard.theme';

export function readStoredThemePreference(): ThemePreference {
  try {
    const stored = localStorage.getItem(THEME_STORAGE_KEY);
    return stored === 'light' || stored === 'dark' ? stored : 'system';
  } catch {
    return 'system';
  }
}

export function applyThemePreference(preference: ThemePreference): void {
  const root = document.documentElement;
  if (preference === 'system') {
    delete root.dataset.theme;
  } else {
    root.dataset.theme = preference;
  }
  try {
    if (preference === 'system') {
      localStorage.removeItem(THEME_STORAGE_KEY);
    } else {
      localStorage.setItem(THEME_STORAGE_KEY, preference);
    }
  } catch {
    // Best-effort persistence only — the toggle still works for this visit.
  }
}

const CYCLE: Record<ThemePreference, ThemePreference> = {
  system: 'light',
  light: 'dark',
  dark: 'system',
};

export const nextThemePreference = (current: ThemePreference): ThemePreference => CYCLE[current];
