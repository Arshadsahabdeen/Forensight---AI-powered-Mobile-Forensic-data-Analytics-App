import axios from 'axios';

export function friendlyError(error: unknown, fallback = 'Something went wrong. Please try again.') {
  if (axios.isAxiosError(error)) {
    if (!error.response) {
      return 'Unable to reach the server. Check that the backend is running and try again.';
    }

    const message = error.response.data?.message ?? error.response.data?.error;
    if (typeof message === 'string' && message.trim()) {
      return message;
    }

    switch (error.response.status) {
      case 401:
        return 'Your session has expired. Please sign in again.';
      case 403:
        return 'You do not have permission to perform this action.';
      case 404:
        return 'The requested record could not be found.';
      case 409:
        return 'A record with these details already exists.';
      case 500:
        return 'The server hit an error while processing this request.';
      default:
        return fallback;
    }
  }

  return fallback;
}
