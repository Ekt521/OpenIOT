import { createInitialState } from '../domain/seed'
import type { PlatformState } from '../domain/types'

const STORAGE_KEY = 'openiot-platform-v1'

function readState(): PlatformState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) return { ...createInitialState(), ...JSON.parse(raw) as Partial<PlatformState> }
  } catch {
    localStorage.removeItem(STORAGE_KEY)
  }
  return createInitialState()
}

let state = readState()
const listeners = new Set<(value: PlatformState) => void>()

function publish() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch {
    // Keep the in-memory session usable when browser storage is unavailable.
  }
  listeners.forEach((listener) => listener(state))
}

export const platformRepository = {
  get(): PlatformState {
    return state
  },
  subscribe(listener: (value: PlatformState) => void): () => void {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },
  update(mutator: (current: PlatformState) => PlatformState): PlatformState {
    state = mutator(state)
    publish()
    return state
  },
  reset(): PlatformState {
    state = createInitialState()
    publish()
    return state
  },
  storageKey: STORAGE_KEY,
}
