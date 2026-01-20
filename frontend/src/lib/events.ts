type EventCallback = () => void;

const listeners: { [key: string]: EventCallback[] } = {};

export const events = {
  on(event: string, callback: EventCallback) {
    if (!listeners[event]) {
      listeners[event] = [];
    }
    listeners[event].push(callback);
    return () => {
      listeners[event] = listeners[event].filter((cb) => cb !== callback);
    };
  },

  emit(event: string) {
    if (listeners[event]) {
      listeners[event].forEach((callback) => callback());
    }
  },
};

export const BRIEFS_CHANGED = 'briefs:changed';
