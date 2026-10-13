const rawBase = import.meta.env.BASE_URL || '/';
const base = rawBase.endsWith('/') ? rawBase.slice(0, -1) : rawBase;

/**
 * Ensures an internal link is prefixed with the site's base URL path
 * so that navigation works identically in local development and production.
 */
export function formatUrl(path: string): string {
  if (
    !path ||
    path.startsWith('http://') ||
    path.startsWith('https://') ||
    path.startsWith('#') ||
    path.startsWith('mailto:')
  ) {
    return path;
  }

  const cleanPath = path.startsWith('/') ? path : `/${path}`;

  if (base && cleanPath.startsWith(base)) {
    return cleanPath;
  }

  return `${base}${cleanPath}`;
}
