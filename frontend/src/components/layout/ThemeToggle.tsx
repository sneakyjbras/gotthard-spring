import { Button } from '@/components/primitives';
import { useThemePreference } from '@/lib/theme/useThemePreference';
import type { ThemePreference } from '@/lib/theme/theme';

const LABEL: Record<ThemePreference, string> = { system: 'Auto', light: 'Light', dark: 'Dark' };

export function ThemeToggle() {
  const { preference, cyclePreference } = useThemePreference();

  return (
    <Button
      variant="ghost"
      size="sm"
      onClick={cyclePreference}
      aria-label={`Theme: ${LABEL[preference]}. Activate to change.`}
    >
      {LABEL[preference]}
    </Button>
  );
}
