import type { SessionUser } from '../lib/authClient';

export async function buildAuthHeaders(
  user: SessionUser | null,
  options: {
    includeContentType?: boolean;
    profileId?: string | null;
    headers?: HeadersInit;
  } = {},
): Promise<Record<string, string>> {
  const result = normalizeHeaders(options.headers);

  if (options.includeContentType !== false && !result['Content-Type']) {
    result['Content-Type'] = 'application/json';
  }

  if (!user) return result;

  if (user.accessToken) {
    result.Authorization = `Bearer ${user.accessToken}`;
  }

  result['x-user-id'] = user.uid;
  if (options.profileId) {
    result['x-profile-id'] = options.profileId;
  }

  return result;
}

function normalizeHeaders(headers?: HeadersInit): Record<string, string> {
  if (!headers) return {};
  if (headers instanceof Headers) {
    return Object.fromEntries(headers.entries());
  }
  if (Array.isArray(headers)) {
    return Object.fromEntries(headers);
  }
  return { ...headers };
}
