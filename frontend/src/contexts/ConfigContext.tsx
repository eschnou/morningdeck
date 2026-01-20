import React, { createContext, useContext, useState, useEffect } from 'react';
import { apiClient, PublicConfig } from '@/lib/api';

interface ConfigContextType {
  config: PublicConfig | null;
  isLoading: boolean;
}

const defaultConfig: PublicConfig = {
  emailVerificationEnabled: true,
  selfHostedMode: false,
};

const ConfigContext = createContext<ConfigContextType | undefined>(undefined);

export const ConfigProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [config, setConfig] = useState<PublicConfig | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchConfig = async () => {
      try {
        const configData = await apiClient.getPublicConfig();
        setConfig(configData);
      } catch (error) {
        console.error('Failed to fetch public config:', error);
        // Use default config on error
        setConfig(defaultConfig);
      } finally {
        setIsLoading(false);
      }
    };

    fetchConfig();
  }, []);

  return (
    <ConfigContext.Provider value={{ config, isLoading }}>
      {children}
    </ConfigContext.Provider>
  );
};

export const useConfig = () => {
  const context = useContext(ConfigContext);
  if (context === undefined) {
    throw new Error('useConfig must be used within a ConfigProvider');
  }
  return context;
};
