import { Component, signal, ElementRef, ViewChild, AfterViewChecked, ChangeDetectorRef } from '@angular/core'; 
import { RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http'; // 1. Import HttpClient
import { timeout } from 'rxjs';

interface Message {
  text: string;
  isMe: boolean;
}

interface UserQueryRequest {
  model: string | null;
  query: string;
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, FormsModule], // Note: HttpClient doesn't need to be imported here if registered in app.config.ts
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements AfterViewChecked {
  protected readonly title = signal('My Simple Chat'); 

  @ViewChild('scrollContainer') private myScrollContainer!: ElementRef;

  messages = signal<Message[]>([
    { text: "Hey there! Ask me anything, and I'll queue it for the agent.", isMe: false }
  ]);

  newMessageText = '';

  // 1. Add your scrollable choices & a variable for the selected one
  availableChoices: string[] = [
    'qwen2:0.5b',
    'qwen2.7b'
    
  ];
  selectedChoice: string | null = null;

  constructor(
    private cdr: ChangeDetectorRef,
    private http: HttpClient
  ) {}

  // 2. Helper method to select/deselect a choice chip
  selectChoice(choice: string) {
    if (this.selectedChoice === choice) {
      this.selectedChoice = null; // Toggle off if clicked again
    } else {
      this.selectedChoice = choice;
    }
  }
  

  sendMessage() {
  if (!this.newMessageText.trim()) return;

  const rawQuery = this.newMessageText.trim();
  const selectedModel = this.selectedChoice;

  // Render cleanly in chat UI (showing model tag visually to user if desired)
  const displayText = selectedModel 
    ? `[${selectedModel}] ${rawQuery}` 
    : rawQuery;

  this.messages.update(allMessages => [
    ...allMessages,
    { text: displayText, isMe: true }
  ]);

  // Build structured JSON payload
  const requestBody: UserQueryRequest = {
    model: selectedModel,
    query: rawQuery
  };

  // Clear input & reset selected tag
  this.newMessageText = ''; 
  this.selectedChoice = null;

  // POST JSON payload to backend
  this.http.post('http://localhost:8080/api/ask', requestBody, { responseType: 'text' })
    .pipe(timeout(300000))
    .subscribe({
      next: (serverResponse) => {
        this.messages.update(allMessages => [
          ...allMessages,
          { text: serverResponse, isMe: false }
        ]);
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error("Connection failed or timed out!", err);
        const isTimeout = err.name === 'TimeoutError';
        const errorMessage = isTimeout 
          ? "Error: Request timed out while waiting for the AI agent." 
          : "Error: Could not reach the Gateway Server.";

        this.messages.update(allMessages => [
          ...allMessages,
          { text: errorMessage, isMe: false }
        ]);
        this.cdr.detectChanges();
      }
    });
}

  ngAfterViewChecked() {
    this.scrollToBottom();
  }

  private scrollToBottom(): void {
    try {
      this.myScrollContainer.nativeElement.scrollTop = this.myScrollContainer.nativeElement.scrollHeight;
    } catch (err) {}
  }
}