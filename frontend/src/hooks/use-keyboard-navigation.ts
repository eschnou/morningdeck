import { useEffect, useCallback } from 'react';

interface UseKeyboardNavigationOptions {
  itemCount: number;
  selectedIndex: number;
  onSelect: (index: number) => void;
  onOpen: () => void;
  onClose: () => void;
  onExternalLink: () => void;
  onToggleHelp: () => void;
  onToggleRead?: () => void;
  onToggleSaved?: () => void;
  onPrevSource?: () => void;
  onNextSource?: () => void;
  isPanelOpen: boolean;
  disabled?: boolean;
}

export function useKeyboardNavigation({
  itemCount,
  selectedIndex,
  onSelect,
  onOpen,
  onClose,
  onExternalLink,
  onToggleHelp,
  onToggleRead,
  onToggleSaved,
  onPrevSource,
  onNextSource,
  isPanelOpen,
  disabled = false,
}: UseKeyboardNavigationOptions) {
  const handleKeyDown = useCallback(
    (event: KeyboardEvent) => {
      if (disabled) return;

      // Skip if focus is in an input element
      const activeElement = document.activeElement;
      const tagName = activeElement?.tagName.toLowerCase();
      if (tagName === 'input' || tagName === 'textarea' || tagName === 'select') {
        return;
      }

      // Also skip if element is contenteditable
      if (activeElement?.getAttribute('contenteditable') === 'true') {
        return;
      }

      const key = event.key.toLowerCase();

      switch (key) {
        case 'j': {
          event.preventDefault();
          if (itemCount === 0) return;

          const nextIndex = Math.min(selectedIndex + 1, itemCount - 1);
          if (nextIndex !== selectedIndex || selectedIndex === -1) {
            if (isPanelOpen) {
              // When panel is open, navigate to next item
              onSelect(nextIndex === selectedIndex ? selectedIndex : nextIndex);
              if (nextIndex !== selectedIndex) {
                onOpen();
              }
            } else {
              // Initialize selection if none, otherwise move down
              onSelect(selectedIndex === -1 ? 0 : nextIndex);
            }
          }
          break;
        }

        case 'k': {
          event.preventDefault();
          if (itemCount === 0) return;

          const prevIndex = Math.max(selectedIndex - 1, 0);
          if (prevIndex !== selectedIndex || selectedIndex === -1) {
            if (isPanelOpen) {
              // When panel is open, navigate to previous item
              onSelect(prevIndex === selectedIndex ? selectedIndex : prevIndex);
              if (prevIndex !== selectedIndex) {
                onOpen();
              }
            } else {
              // Initialize selection if none, otherwise move up
              onSelect(selectedIndex === -1 ? 0 : prevIndex);
            }
          }
          break;
        }

        case 'enter':
        case 'o': {
          event.preventDefault();
          if (selectedIndex >= 0 && selectedIndex < itemCount) {
            onOpen();
          }
          break;
        }

        case 'x':
        case 'escape': {
          if (isPanelOpen) {
            event.preventDefault();
            onClose();
          }
          break;
        }

        case 'e': {
          event.preventDefault();
          if (selectedIndex >= 0 && selectedIndex < itemCount) {
            onExternalLink();
          }
          break;
        }

        case '?': {
          event.preventDefault();
          onToggleHelp();
          break;
        }

        case 'r': {
          if (selectedIndex >= 0 && selectedIndex < itemCount && onToggleRead) {
            event.preventDefault();
            onToggleRead();
          }
          break;
        }

        case 's': {
          if (selectedIndex >= 0 && selectedIndex < itemCount && onToggleSaved) {
            event.preventDefault();
            onToggleSaved();
          }
          break;
        }

        case 'h': {
          if (onPrevSource && !isPanelOpen) {
            event.preventDefault();
            onPrevSource();
          }
          break;
        }

        case 'l': {
          if (onNextSource && !isPanelOpen) {
            event.preventDefault();
            onNextSource();
          }
          break;
        }
      }
    },
    [
      disabled,
      itemCount,
      selectedIndex,
      isPanelOpen,
      onSelect,
      onOpen,
      onClose,
      onExternalLink,
      onToggleHelp,
      onToggleRead,
      onToggleSaved,
      onPrevSource,
      onNextSource,
    ]
  );

  useEffect(() => {
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [handleKeyDown]);
}
