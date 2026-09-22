import React from 'react';
import ReactDOM from 'react-dom/client';
import { ReactFlowProvider } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import './styles.css';
import { App } from './App';
import { OrderEditor } from './OrderEditor';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ReactFlowProvider>
      {import.meta.env.MODE === 'example' ? <OrderEditor /> : <App />}
    </ReactFlowProvider>
  </React.StrictMode>,
);
