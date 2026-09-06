import { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("Erreur interceptée par ErrorBoundary :", error, errorInfo);
  }

  public render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen bg-dark-950 text-white flex flex-col items-center justify-center p-6 text-center">
          <div className="w-16 h-16 rounded-2xl bg-rose-500/20 border border-rose-500/50 flex items-center justify-center mb-4 text-rose-400">
            <AlertTriangle className="w-8 h-8" />
          </div>
          <h2 className="text-xl font-bold mb-2">Une anomalie d'affichage est survenue</h2>
          <p className="text-xs text-slate-400 font-mono max-w-md mb-6 bg-dark-900 p-3 rounded-xl border border-slate-800">
            {this.state.error?.message || "Erreur inconnue"}
          </p>
          <button
            onClick={() => window.location.reload()}
            className="bg-brand-600 hover:bg-brand-500 text-white font-bold py-2.5 px-5 rounded-xl flex items-center space-x-2 text-xs transition-colors cursor-pointer"
          >
            <RefreshCw className="w-4 h-4" />
            <span>Recharger la page</span>
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
