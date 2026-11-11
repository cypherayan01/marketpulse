import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createApi } from './api'
import App from './App'
import './styles.css'

// Set VITE_API_BASE_URL to the Render backend, e.g. https://marketpulse-backend.onrender.com
const api = createApi(import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080')

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App api={api} />
  </StrictMode>,
)
