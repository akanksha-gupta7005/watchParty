import type { ReactElement } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { getAuth } from './lib/auth';
import Home from './pages/Home';
import Login from './pages/Login';
import Room from './pages/Room';

/** Pages that need a login. Not logged in -> go to /login and come back afterwards. */
function RequireAuth({ children }: { children: ReactElement }) {
  const location = useLocation();
  if (!getAuth()) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <Home />
          </RequireAuth>
        }
      />
      <Route
        path="/room/:code"
        element={
          <RequireAuth>
            <Room />
          </RequireAuth>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
