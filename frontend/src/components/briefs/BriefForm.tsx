import { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Checkbox } from '@/components/ui/checkbox';
import { Switch } from '@/components/ui/switch';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Loader2, Mail } from 'lucide-react';
import { z } from 'zod';
import type { DayBriefDTO, SourceDTO, UpdateDayBriefRequest, BriefingFrequency, DayOfWeek } from '@/types';

interface BriefFormProps {
  brief: DayBriefDTO;
  sources: SourceDTO[];
  onSubmit: (data: UpdateDayBriefRequest) => void;
  onCancel: () => void;
  isLoading?: boolean;
}

const TIMEZONES = [
  'UTC',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'Europe/London',
  'Europe/Paris',
  'Europe/Berlin',
  'Asia/Tokyo',
  'Asia/Shanghai',
  'Australia/Sydney',
];

const DAYS_OF_WEEK: { value: DayOfWeek; label: string }[] = [
  { value: 'MONDAY', label: 'Monday' },
  { value: 'TUESDAY', label: 'Tuesday' },
  { value: 'WEDNESDAY', label: 'Wednesday' },
  { value: 'THURSDAY', label: 'Thursday' },
  { value: 'FRIDAY', label: 'Friday' },
  { value: 'SATURDAY', label: 'Saturday' },
  { value: 'SUNDAY', label: 'Sunday' },
];

const formSchema = z.object({
  title: z.string().min(1, 'Title is required').max(255),
  briefing: z.string().min(1, 'Briefing criteria is required'),
  sourceIds: z.array(z.string()).min(1, 'Select at least one source'),
  status: z.enum(['ACTIVE', 'PAUSED']),
});

export function BriefForm({ brief, sources, onSubmit, onCancel, isLoading = false }: BriefFormProps) {
  // Derive initial sourceIds from sources belonging to this brief
  const getSourceIds = () => sources.filter(s => s.briefingId === brief.id).map(s => s.id);

  const [formData, setFormData] = useState({
    title: brief.title,
    description: brief.description || '',
    briefing: brief.briefing,
    sourceIds: getSourceIds(),
    frequency: brief.frequency,
    scheduleDayOfWeek: brief.scheduleDayOfWeek || ('MONDAY' as DayOfWeek),
    scheduleTime: brief.scheduleTime.substring(0, 5), // HH:mm
    timezone: brief.timezone,
    status: brief.status as 'ACTIVE' | 'PAUSED',
    emailDeliveryEnabled: brief.emailDeliveryEnabled ?? false,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    setFormData({
      title: brief.title,
      description: brief.description || '',
      briefing: brief.briefing,
      sourceIds: getSourceIds(),
      frequency: brief.frequency,
      scheduleDayOfWeek: brief.scheduleDayOfWeek || ('MONDAY' as DayOfWeek),
      scheduleTime: brief.scheduleTime.substring(0, 5),
      timezone: brief.timezone,
      status: brief.status as 'ACTIVE' | 'PAUSED',
      emailDeliveryEnabled: brief.emailDeliveryEnabled ?? false,
    });
  }, [brief, sources]);

  const handleSourceToggle = (sourceId: string) => {
    setFormData((prev) => ({
      ...prev,
      sourceIds: prev.sourceIds.includes(sourceId)
        ? prev.sourceIds.filter((id) => id !== sourceId)
        : [...prev.sourceIds, sourceId],
    }));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setErrors({});

    try {
      formSchema.parse({
        title: formData.title,
        briefing: formData.briefing,
        sourceIds: formData.sourceIds,
        status: formData.status,
      });

      onSubmit({
        title: formData.title,
        description: formData.description || undefined,
        briefing: formData.briefing,
        sourceIds: formData.sourceIds,
        frequency: formData.frequency,
        scheduleDayOfWeek: formData.frequency === 'WEEKLY' ? formData.scheduleDayOfWeek : undefined,
        scheduleTime: formData.scheduleTime + ':00',
        timezone: formData.timezone,
        status: formData.status,
        emailDeliveryEnabled: formData.emailDeliveryEnabled,
      });
    } catch (error: unknown) {
      if (error instanceof z.ZodError) {
        const fieldErrors: Record<string, string> = {};
        error.errors.forEach((err) => {
          if (err.path[0]) {
            fieldErrors[err.path[0].toString()] = err.message;
          }
        });
        setErrors(fieldErrors);
      }
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      {/* Basic Info */}
      <div className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="title">Title</Label>
          <Input
            id="title"
            value={formData.title}
            onChange={(e) => setFormData({ ...formData, title: e.target.value })}
            disabled={isLoading}
            className={errors.title ? 'border-destructive' : ''}
          />
          {errors.title && (
            <p className="text-sm text-destructive">{errors.title}</p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="description">Description</Label>
          <Input
            id="description"
            value={formData.description}
            onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            disabled={isLoading}
          />
        </div>
      </div>

      {/* Content */}
      <div className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="briefing">Briefing Criteria</Label>
          <Textarea
            id="briefing"
            value={formData.briefing}
            onChange={(e) => setFormData({ ...formData, briefing: e.target.value })}
            disabled={isLoading}
            className={errors.briefing ? 'border-destructive' : ''}
            rows={4}
          />
          {errors.briefing && (
            <p className="text-sm text-destructive">{errors.briefing}</p>
          )}
        </div>

        <div className="space-y-2">
          <Label>Sources</Label>
          {sources.length === 0 ? (
            <p className="text-sm text-muted-foreground py-2">
              No active sources available.
            </p>
          ) : (
            <div className="border rounded-md p-3 space-y-2 max-h-40 overflow-y-auto">
              {sources.map((source) => (
                <div key={source.id} className="flex items-center space-x-2">
                  <Checkbox
                    id={`source-${source.id}`}
                    checked={formData.sourceIds.includes(source.id)}
                    onCheckedChange={() => handleSourceToggle(source.id)}
                    disabled={isLoading}
                  />
                  <label
                    htmlFor={`source-${source.id}`}
                    className="text-sm cursor-pointer flex-1"
                  >
                    {source.name}
                  </label>
                </div>
              ))}
            </div>
          )}
          {errors.sourceIds && (
            <p className="text-sm text-destructive">{errors.sourceIds}</p>
          )}
        </div>
      </div>

      {/* Schedule */}
      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="frequency">Frequency</Label>
          <Select
            value={formData.frequency}
            onValueChange={(value: BriefingFrequency) =>
              setFormData({ ...formData, frequency: value })
            }
            disabled={isLoading}
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="DAILY">Daily</SelectItem>
              <SelectItem value="WEEKLY">Weekly</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {formData.frequency === 'WEEKLY' && (
          <div className="space-y-2">
            <Label htmlFor="scheduleDayOfWeek">Day</Label>
            <Select
              value={formData.scheduleDayOfWeek}
              onValueChange={(value: DayOfWeek) =>
                setFormData({ ...formData, scheduleDayOfWeek: value })
              }
              disabled={isLoading}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {DAYS_OF_WEEK.map((day) => (
                  <SelectItem key={day.value} value={day.value}>
                    {day.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}

        <div className="space-y-2">
          <Label htmlFor="scheduleTime">Time</Label>
          <Input
            id="scheduleTime"
            type="time"
            value={formData.scheduleTime}
            onChange={(e) => setFormData({ ...formData, scheduleTime: e.target.value })}
            disabled={isLoading}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="timezone">Timezone</Label>
          <Select
            value={formData.timezone}
            onValueChange={(value) => setFormData({ ...formData, timezone: value })}
            disabled={isLoading}
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {TIMEZONES.map((tz) => (
                <SelectItem key={tz} value={tz}>
                  {tz}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <Label htmlFor="status">Status</Label>
          <Select
            value={formData.status}
            onValueChange={(value: 'ACTIVE' | 'PAUSED') =>
              setFormData({ ...formData, status: value })
            }
            disabled={isLoading}
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ACTIVE">Active</SelectItem>
              <SelectItem value="PAUSED">Paused</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {/* Email Delivery */}
      <div className="flex items-center justify-between rounded-lg border p-4">
        <div className="space-y-0.5">
          <div className="flex items-center gap-2">
            <Mail className="h-4 w-4 text-muted-foreground" />
            <Label htmlFor="emailDeliveryEnabled" className="font-medium">
              Email report delivery
            </Label>
          </div>
          <p className="text-sm text-muted-foreground">
            Send generated reports to your email
          </p>
        </div>
        <Switch
          id="emailDeliveryEnabled"
          checked={formData.emailDeliveryEnabled}
          onCheckedChange={(checked) =>
            setFormData({ ...formData, emailDeliveryEnabled: checked })
          }
          disabled={isLoading}
        />
      </div>

      <div className="flex justify-end gap-2 pt-4">
        <Button type="button" variant="outline" onClick={onCancel} disabled={isLoading}>
          Cancel
        </Button>
        <Button type="submit" disabled={isLoading}>
          {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          Save Changes
        </Button>
      </div>
    </form>
  );
}
