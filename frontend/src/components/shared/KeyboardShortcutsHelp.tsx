import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';

export interface KeyboardShortcut {
  key: string;
  description: string;
}

const defaultShortcuts: KeyboardShortcut[] = [
  { key: 'j', description: 'Move to next item' },
  { key: 'k', description: 'Move to previous item' },
  { key: 'h', description: 'Go to previous source' },
  { key: 'l', description: 'Go to next source' },
  { key: 'Enter / o', description: 'Open selected item' },
  { key: 'x / Esc', description: 'Close panel' },
  { key: 'e', description: 'Open external link' },
  { key: 'r', description: 'Toggle read' },
  { key: 's', description: 'Toggle saved' },
  { key: '?', description: 'Toggle this help' },
];

export const feedShortcuts: KeyboardShortcut[] = [
  { key: 'j', description: 'Move to next item' },
  { key: 'k', description: 'Move to previous item' },
  { key: 'Enter / o', description: 'Open selected item' },
  { key: 'x / Esc', description: 'Close panel' },
  { key: 'e', description: 'Open external link' },
  { key: 'r', description: 'Toggle read' },
  { key: 's', description: 'Toggle saved' },
  { key: '?', description: 'Toggle this help' },
];

interface KeyboardShortcutsHelpProps {
  open: boolean;
  onClose: () => void;
  shortcuts?: KeyboardShortcut[];
}

export function KeyboardShortcutsHelp({ open, onClose, shortcuts = defaultShortcuts }: KeyboardShortcutsHelpProps) {
  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Keyboard Shortcuts</DialogTitle>
        </DialogHeader>
        <div className="grid gap-2 py-4">
          {shortcuts.map((shortcut) => (
            <div
              key={shortcut.key}
              className="flex items-center justify-between py-1"
            >
              <span className="text-sm text-muted-foreground">
                {shortcut.description}
              </span>
              <kbd className="px-2 py-1 text-xs font-mono bg-muted rounded border">
                {shortcut.key}
              </kbd>
            </div>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  );
}
