import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { PageHeader } from '@/components/layout/PageHeader';
import { StatusBadge } from '@/components/shared/StatusBadge';
import { EmptyState } from '@/components/shared/EmptyState';
import { PaginationControls } from '@/components/shared/PaginationControls';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { ArrowLeft, FileText, Calendar, ListOrdered } from 'lucide-react';
import { format, formatDistanceToNow } from 'date-fns';
import { parseApiDate } from '@/lib/utils';
import { apiClient } from '@/lib/api';
import { toast } from '@/hooks/use-toast';
import type { DailyReportDTO, DayBriefDTO } from '@/types';

export default function BriefReportsPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [brief, setBrief] = useState<DayBriefDTO | null>(null);
  const [reports, setReports] = useState<DailyReportDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const fetchData = useCallback(async (page = 0) => {
    setIsLoading(true);
    try {
      const [briefData, reportsData] = await Promise.all([
        apiClient.getDayBrief(id!),
        apiClient.getDayBriefReports(id!, page, 10),
      ]);
      setBrief(briefData);
      setReports(reportsData.content);
      setTotalPages(reportsData.totalPages);
      setCurrentPage(reportsData.number);
    } catch (error: unknown) {
      toast({
        title: 'Failed to load reports',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
      navigate(`/briefs/${id}`);
    } finally {
      setIsLoading(false);
    }
  }, [id, navigate]);

  useEffect(() => {
    if (id) {
      fetchData(0);
    }
  }, [id, fetchData]);

  const handleReportClick = (report: DailyReportDTO) => {
    navigate(`/briefs/${id}/reports/${report.id}`);
  };

  if (isLoading && !brief) {
    return (
      <>
        <PageHeader title="Loading..." />
        <main className="flex-1 p-4 max-w-3xl">
          <Skeleton className="h-64" />
        </main>
      </>
    );
  }

  return (
    <>
      <PageHeader title={brief ? `${brief.title} - Reports` : 'Reports'}>
        <Link to={`/briefs/${id}`}>
          <Button variant="ghost" size="sm">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to Brief
          </Button>
        </Link>
      </PageHeader>

      <main className="flex-1 p-4 max-w-3xl">
        {isLoading ? (
          <div className="space-y-4">
            {[...Array(3)].map((_, i) => (
              <Skeleton key={i} className="h-24" />
            ))}
          </div>
        ) : reports.length === 0 ? (
          <EmptyState
            icon={FileText}
            title="No reports yet"
            description="Reports will appear here after the brief is executed."
          />
        ) : (
          <>
            <div className="space-y-4">
              {reports.map((report) => (
                <Card
                  key={report.id}
                  className="cursor-pointer transition-colors hover:bg-muted/50"
                  onClick={() => handleReportClick(report)}
                >
                  <CardContent className="p-4">
                    <div className="flex items-start justify-between gap-4">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-2">
                          <FileText className="h-4 w-4 text-muted-foreground" />
                          <span className="font-medium">
                            {format(new Date(report.generatedAt), 'EEEE, MMMM d, yyyy')}
                          </span>
                          <StatusBadge status={report.status} size="sm" />
                        </div>
                        <div className="flex items-center gap-4 text-sm text-muted-foreground">
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <div className="flex items-center gap-1 cursor-default">
                                <Calendar className="h-3 w-3" />
                                <span>
                                  {formatDistanceToNow(parseApiDate(report.generatedAt), { addSuffix: true })}
                                </span>
                              </div>
                            </TooltipTrigger>
                            <TooltipContent>
                              {format(parseApiDate(report.generatedAt), 'PPpp')}
                            </TooltipContent>
                          </Tooltip>
                          <div className="flex items-center gap-1">
                            <ListOrdered className="h-3 w-3" />
                            <span>{report.itemCount} items</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
            <PaginationControls
              currentPage={currentPage}
              totalPages={totalPages}
              onPageChange={fetchData}
              disabled={isLoading}
            />
          </>
        )}
      </main>
    </>
  );
}
