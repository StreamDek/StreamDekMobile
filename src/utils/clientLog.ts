import { API_BASE } from '../constants/api';

type ClientLogLevel = 'info' | 'warn' | 'error';

interface ClientLogPayload {
  level: ClientLogLevel;
  tag: string;
  message: string;
  userId?: string | null;
  context?: Record<string, unknown>;
}

export async function postClientLog(payload: ClientLogPayload): Promise<void> {
  try {
    await fetch(`${API_BASE}/debug/client-log`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(payload.userId ? { 'x-user-id': payload.userId } : {}),
      },
      body: JSON.stringify(payload),
    });
  } catch {
    // Best-effort debug logging only.
  }
}
