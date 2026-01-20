import React from 'react';
import { useLocation, Link } from 'react-router-dom';
import { SidebarTrigger } from '@/components/ui/sidebar';
import { Separator } from '@/components/ui/separator';
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb';

export interface BreadcrumbItem {
  label: string;
  path?: string;
}

interface PageHeaderProps {
  title?: string;
  description?: string;
  breadcrumbs?: BreadcrumbItem[];
  children?: React.ReactNode;
  actions?: React.ReactNode;
}

const routeLabels: Record<string, string> = {
  home: 'Home',
  sources: 'Sources',
  briefs: 'Briefs',
  reports: 'Reports',
  settings: 'Settings',
  admin: 'Admin',
  writers: 'Writers',
  channels: 'Channels',
  posts: 'Posts',
};

export function PageHeader({ title, description, breadcrumbs: customBreadcrumbs, children, actions }: PageHeaderProps) {
  const location = useLocation();

  const getAutoBreadcrumbs = () => {
    const segments = location.pathname.split('/').filter(Boolean);
    const breadcrumbs: { label: string; path: string; isLast: boolean }[] = [];

    let currentPath = '';
    segments.forEach((segment, index) => {
      currentPath += `/${segment}`;
      const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(segment);
      const label = isUuid ? 'Detail' : routeLabels[segment] || segment;
      breadcrumbs.push({
        label,
        path: currentPath,
        isLast: index === segments.length - 1,
      });
    });

    return breadcrumbs;
  };

  const breadcrumbs = customBreadcrumbs
    ? customBreadcrumbs.map((b, i) => ({
        label: b.label,
        path: b.path || '',
        isLast: i === customBreadcrumbs.length - 1,
      }))
    : getAutoBreadcrumbs();

  return (
    <header className="sticky top-0 z-10 flex h-14 shrink-0 items-center gap-2 border-b bg-background px-4">
      <SidebarTrigger className="-ml-1" />
      <Separator orientation="vertical" className="mr-2 h-4" />
      <Breadcrumb>
        <BreadcrumbList>
          {breadcrumbs.map((crumb, index) => (
            <React.Fragment key={crumb.path}>
              <BreadcrumbItem>
                {crumb.isLast ? (
                  <BreadcrumbPage>{title || crumb.label}</BreadcrumbPage>
                ) : (
                  <BreadcrumbLink asChild>
                    <Link to={crumb.path}>{crumb.label}</Link>
                  </BreadcrumbLink>
                )}
              </BreadcrumbItem>
              {index < breadcrumbs.length - 1 && <BreadcrumbSeparator />}
            </React.Fragment>
          ))}
        </BreadcrumbList>
      </Breadcrumb>
      <div className="ml-auto flex items-center gap-2">{actions || children}</div>
    </header>
  );
}
