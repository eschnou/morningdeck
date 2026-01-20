import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/**
 * Parses an ISO date string from the API as UTC.
 * The backend sends Instant with 'Z' suffix (e.g., "2024-01-15T10:30:00Z").
 * This function handles both new format (with Z) and legacy format (without Z).
 */
export function parseApiDate(dateString: string): Date {
  if (!dateString.endsWith('Z') && !dateString.includes('+') && !dateString.includes('-', 10)) {
    return new Date(dateString + 'Z');
  }
  return new Date(dateString);
}
