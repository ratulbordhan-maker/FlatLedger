// One place for every call to the backend.
// - adds the login token to each request
// - turns error responses into an ApiError with a readable message
// - tells the app to log out when the token is no longer valid

const SESSION_KEY = 'flatmeals.session'

export class ApiError extends Error {
  constructor(status, message, fieldErrors = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

/** The saved login, or null if there is none or it has expired. */
export function readSession() {
  try {
    const session = JSON.parse(localStorage.getItem(SESSION_KEY))
    if (!session?.token) return null
    if (session.expiresAt && new Date(session.expiresAt) <= new Date()) {
      localStorage.removeItem(SESSION_KEY)
      return null
    }
    return session
  } catch {
    return null
  }
}

export function saveSession(session) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session))
}

export function clearSession() {
  localStorage.removeItem(SESSION_KEY)
}

const DEFAULT_MESSAGES = {
  400: 'Some of the details are not valid.',
  401: 'Your session has ended. Sign in again.',
  403: 'You do not have permission to do this.',
  404: 'Not found.',
  409: 'This clashes with existing data.',
  500: 'The server had a problem. Try again in a moment.',
}

/**
 * Calls the backend. Example: await api('/months/2026/10/meals')
 * Paths start after /api. The Vite dev server forwards /api to Spring Boot.
 */
export async function api(path, { method = 'GET', body } = {}) {
  const session = readSession()
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (session) headers.Authorization = `Bearer ${session.token}`

  let response
  try {
    response = await fetch(`/api${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, "Can't reach the server. Check that the backend is running.")
  }

  if (response.status === 204) return null
  const text = await response.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = null
    }
  }

  if (!response.ok) {
    if (response.status === 401 && session) {
      window.dispatchEvent(new Event('flatmeals:signed-out'))
    }
    // An empty 5xx answer means the dev server couldn't reach Spring Boot
    if (!data && response.status >= 500) {
      throw new ApiError(response.status, "Can't reach the server. Check that the backend is running.")
    }
    const message = data?.message || DEFAULT_MESSAGES[response.status] || 'Something went wrong.'
    throw new ApiError(response.status, message, data?.fieldErrors || {})
  }
  return data
}

/**
 * Downloads a file from the backend, e.g. the month's CSV report.
 * A plain link can't send the login token, so we fetch the file with the
 * token, then hand it to the browser as a download.
 */
export async function download(path, fileName) {
  const session = readSession()
  let response
  try {
    response = await fetch(`/api${path}`, {
      headers: session ? { Authorization: `Bearer ${session.token}` } : {},
    })
  } catch {
    throw new ApiError(0, "Can't reach the server. Check that the backend is running.")
  }
  if (!response.ok) {
    if (response.status === 401 && session) {
      window.dispatchEvent(new Event('flatmeals:signed-out'))
    }
    let message = DEFAULT_MESSAGES[response.status] || 'Something went wrong.'
    try {
      message = (await response.json()).message || message
    } catch {
      // the error had no JSON body; keep the default message
    }
    throw new ApiError(response.status, message)
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
