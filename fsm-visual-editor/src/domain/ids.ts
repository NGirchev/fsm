export function createId(prefix: string): string {
  return `${prefix}_${Math.random().toString(36).slice(2, 8)}_${Date.now().toString(36)}`;
}

export function slugifyId(value: string, fallback: string): string {
  const normalized = value
    .trim()
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');

  return normalized || fallback;
}

export function uniqueId(base: string, existing: Set<string>): string {
  let candidate = base;
  let suffix = 2;

  while (existing.has(candidate)) {
    candidate = `${base}_${suffix}`;
    suffix += 1;
  }

  existing.add(candidate);
  return candidate;
}
