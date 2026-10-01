import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './styles.css';

// Note: React.StrictMode is intentionally not used. In development it mounts every
// component twice, which would open two WebSocket connections and create a ghost participant.
createRoot(document.getElementById('root')!).render(
  <BrowserRouter>
    <App />
  </BrowserRouter>,
);
