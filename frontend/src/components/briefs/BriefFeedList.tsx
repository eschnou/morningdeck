import { useState, useEffect, useCallback, useRef } from 'react';
import { Newspaper, Keyboard, Bookmark, Star, CheckCheck, Loader2, Search, X } from 'lucide-react';
import { Skeleton } from '@/components/ui/skeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group';
import { PaginationControls } from '@/components/shared/PaginationControls';
import { EmptyState } from '@/components/shared/EmptyState';
import { ItemDetailPanel } from '@/components/shared/ItemDetailPanel';
import { KeyboardShortcutsHelp, feedShortcuts } from '@/components/shared/KeyboardShortcutsHelp';
import { NewsItemRow } from '@/components/sources/NewsItemRow';
import { apiClient } from '@/lib/api';
import { toast } from '@/hooks/use-toast';
import { useDebounce } from '@/hooks/use-debounce';
import { useKeyboardNavigation } from '@/hooks/use-keyboard-navigation';
import type { NewsItemDTO, ReadFilter } from '@/types';

interface BriefFeedListProps {
  briefingId: string;
}

export function BriefFeedList({ briefingId }: BriefFeedListProps) {
  const [items, setItems] = useState<NewsItemDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const [isPanelOpen, setIsPanelOpen] = useState(false);
  const [showHelp, setShowHelp] = useState(false);
  const [readFilter, setReadFilter] = useState<ReadFilter>('ALL');
  const [savedFilter, setSavedFilter] = useState(false);
  const [topScoreFilter, setTopScoreFilter] = useState(false);
  const [isMarkingAllRead, setIsMarkingAllRead] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const debouncedSearchQuery = useDebounce(searchQuery, 200);
  const itemRefs = useRef<(HTMLDivElement | null)[]>([]);
  const searchInputRef = useRef<HTMLInputElement>(null);

  const fetchItems = useCallback(async (page = 0) => {
    setIsLoading(true);
    try {
      const response = await apiClient.getBriefingItems(
        briefingId,
        page,
        20,
        undefined,
        readFilter,
        savedFilter || undefined,
        topScoreFilter ? 70 : undefined,
        debouncedSearchQuery || undefined
      );
      setItems(response.content);
      setTotalPages(response.totalPages);
      setTotalElements(response.totalElements);
      setCurrentPage(response.number);
      setSelectedIndex(-1);
    } catch (error: unknown) {
      toast({
        title: 'Failed to load items',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    } finally {
      setIsLoading(false);
    }
  }, [briefingId, readFilter, savedFilter, topScoreFilter, debouncedSearchQuery]);

  useEffect(() => {
    fetchItems(0);
  }, [fetchItems]);

  const handlePageChange = (page: number) => {
    fetchItems(page);
  };

  const handleRowClick = (index: number) => {
    setSelectedIndex(index);
    setIsPanelOpen(true);
  };

  const handleClosePanel = () => {
    setIsPanelOpen(false);
  };

  const handleItemUpdated = useCallback((updatedItem: NewsItemDTO) => {
    setItems((prevItems) =>
      prevItems.map((item) =>
        item.id === updatedItem.id ? updatedItem : item
      )
    );
  }, []);

  const handleSelect = useCallback((index: number) => {
    setSelectedIndex(index);
    itemRefs.current[index]?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }, []);

  const handleOpen = useCallback(() => {
    if (selectedIndex >= 0 && selectedIndex < items.length) {
      setIsPanelOpen(true);
    }
  }, [selectedIndex, items.length]);

  const handleKeyboardExternalLink = useCallback(() => {
    if (selectedIndex >= 0 && selectedIndex < items.length) {
      window.open(items[selectedIndex].link, '_blank', 'noopener,noreferrer');
    }
  }, [selectedIndex, items]);

  const handleKeyboardToggleRead = useCallback(() => {
    if (selectedIndex >= 0 && selectedIndex < items.length) {
      const item = items[selectedIndex];
      apiClient.toggleRead(item.id).then(handleItemUpdated).catch(console.error);
    }
  }, [selectedIndex, items, handleItemUpdated]);

  const handleKeyboardToggleSaved = useCallback(() => {
    if (selectedIndex >= 0 && selectedIndex < items.length) {
      const item = items[selectedIndex];
      apiClient.toggleSaved(item.id).then(handleItemUpdated).catch(console.error);
    }
  }, [selectedIndex, items, handleItemUpdated]);

  useKeyboardNavigation({
    itemCount: items.length,
    selectedIndex,
    onSelect: handleSelect,
    onOpen: handleOpen,
    onClose: handleClosePanel,
    onExternalLink: handleKeyboardExternalLink,
    onToggleHelp: () => setShowHelp((prev) => !prev),
    onToggleRead: handleKeyboardToggleRead,
    onToggleSaved: handleKeyboardToggleSaved,
    isPanelOpen,
    disabled: isLoading,
  });

  const handleExternalLink = (e: React.MouseEvent, item: NewsItemDTO) => {
    e.stopPropagation();
    window.open(item.link, '_blank', 'noopener,noreferrer');
  };

  const handleMarkAllAsRead = async () => {
    setIsMarkingAllRead(true);
    try {
      const result = await apiClient.markAllReadByBriefing(briefingId);
      toast({
        title: 'Marked as read',
        description: `${result.updatedCount} items marked as read`,
      });
      fetchItems(currentPage);
    } catch (error: unknown) {
      toast({
        title: 'Failed to mark items as read',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    } finally {
      setIsMarkingAllRead(false);
    }
  };

  const getPageInfo = () => {
    if (totalElements === 0) return null;
    const start = currentPage * 20 + 1;
    const end = Math.min((currentPage + 1) * 20, totalElements);
    return `Showing ${start}-${end} of ${totalElements} items`;
  };

  return (
    <div className="space-y-4">
      {/* Filters header */}
      <div className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <ToggleGroup
            type="single"
            value={readFilter}
            onValueChange={(value) => value && setReadFilter(value as ReadFilter)}
            size="sm"
          >
            <ToggleGroupItem value="ALL" aria-label="Show all items">
              All
            </ToggleGroupItem>
            <ToggleGroupItem value="UNREAD" aria-label="Show unread items">
              Unread
            </ToggleGroupItem>
            <ToggleGroupItem value="READ" aria-label="Show read items">
              Read
            </ToggleGroupItem>
          </ToggleGroup>
          <Button
            variant={savedFilter ? 'default' : 'outline'}
            size="sm"
            onClick={() => setSavedFilter(!savedFilter)}
            className="gap-1"
          >
            <Bookmark className={`h-4 w-4 ${savedFilter ? 'fill-current' : ''}`} />
            <span className="hidden sm:inline">Saved</span>
          </Button>
          <Button
            variant={topScoreFilter ? 'default' : 'outline'}
            size="sm"
            onClick={() => setTopScoreFilter(!topScoreFilter)}
            className="gap-1"
          >
            <Star className={`h-4 w-4 ${topScoreFilter ? 'fill-current' : ''}`} />
            <span className="hidden sm:inline">Top Score</span>
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={handleMarkAllAsRead}
            disabled={isMarkingAllRead || isLoading || items.length === 0}
            className="gap-1"
          >
            {isMarkingAllRead ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <CheckCheck className="h-4 w-4" />
            )}
            <span className="hidden sm:inline">Mark all read</span>
          </Button>
          {/* Search input */}
          <div className="relative w-full sm:flex-1 sm:min-w-48">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              ref={searchInputRef}
              type="search"
              placeholder="Search articles..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-8 pr-8 h-9"
            />
            {searchQuery && (
              <button
                onClick={() => setSearchQuery('')}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
              >
                <X className="h-4 w-4" />
              </button>
            )}
          </div>
        </div>
        {!isLoading && totalElements > 0 && (
          <span className="text-xs sm:text-sm text-muted-foreground">{getPageInfo()}</span>
        )}
      </div>

      {/* Feed content */}
      <div className="rounded-lg border bg-card">
        {isLoading && items.length === 0 ? (
          <div className="space-y-0">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="px-4 py-3 border-b border-border/50 last:border-b-0">
                <Skeleton className="h-4 w-3/4 mb-2" />
                <Skeleton className="h-3 w-1/3" />
              </div>
            ))}
          </div>
        ) : items.length === 0 ? (
          <EmptyState
            icon={debouncedSearchQuery ? Search : Newspaper}
            title={debouncedSearchQuery ? "No results found" : "No items yet"}
            description={
              debouncedSearchQuery
                ? `No articles match "${debouncedSearchQuery}". Try a different search term or remove filters.`
                : "Add sources to this brief to start seeing news items in your feed."
            }
          />
        ) : (
          <>
            <div className="max-h-[600px] overflow-y-auto">
              {items.map((item, index) => (
                <NewsItemRow
                  key={item.id}
                  ref={(el) => {
                    itemRefs.current[index] = el;
                  }}
                  item={item}
                  isSelected={selectedIndex === index}
                  showSourceName
                  onClick={() => handleRowClick(index)}
                  onExternalLink={(e) => handleExternalLink(e, item)}
                />
              ))}
            </div>
            <div className="p-4 border-t border-border/50 flex items-center justify-center sm:justify-between">
              <PaginationControls
                currentPage={currentPage}
                totalPages={totalPages}
                onPageChange={handlePageChange}
                disabled={isLoading}
              />
              <button
                onClick={() => setShowHelp(true)}
                className="hidden sm:flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors"
              >
                <Keyboard className="h-3 w-3" />
                Press ? for shortcuts
              </button>
            </div>
          </>
        )}
      </div>

      <ItemDetailPanel
        item={selectedIndex >= 0 ? items[selectedIndex] : null}
        open={isPanelOpen}
        onClose={handleClosePanel}
        onItemUpdated={handleItemUpdated}
      />

      <KeyboardShortcutsHelp
        open={showHelp}
        onClose={() => setShowHelp(false)}
        shortcuts={feedShortcuts}
      />
    </div>
  );
}
