import axios from 'axios';

/** Human-readable message for an API error; never exposes stack traces or raw payloads. */
export function friendlyError(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (axios.isAxiosError(error)) {
    if (!error.response) {
      return error.code === 'ECONNABORTED'
        ? 'The request timed out. Please try again.'
        : 'Unable to reach the server. Check that the backend is running and try again.';
    }
    const data = error.response.data as { message?: unknown } | Blob | undefined;
    const message = data && !(data instanceof Blob) ? data.message : undefined;
    if (typeof message === 'string' && message.trim()) {
      return message;
    }
    switch (error.response.status) {
      case 400:
        return 'The request was not valid. Check the highlighted fields.';
      case 401:
        return 'Your session has expired. Please sign in again.';
      case 403:
        return 'You do not have permission to perform this action.';
      case 404:
        return 'The requested record could not be found.';
      case 409:
        return 'This conflicts with an existing record.';
      case 413:
        return 'The file is larger than the maximum allowed size.';
      case 503:
        return 'The platform is in maintenance mode. Changes are temporarily disabled.';
      default:
        return fallback;
    }
  }
  return error instanceof Error && error.message ? error.message : fallback;
}

/** Field-level validation messages returned by the backend ({ fields: { name: message } }). */
export function fieldErrors(error: unknown): Record<string, string> {
  if (axios.isAxiosError(error)) {
    const fields = (error.response?.data as { fields?: Record<string, string> } | undefined)?.fields;
    if (fields && typeof fields === 'object') {
      return fields;
    }
  }
  return {};
}

export function statusOf(error: unknown): number | undefined {
  return axios.isAxiosError(error) ? error.response?.status : undefined;
}
