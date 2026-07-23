import { Component, signal, ElementRef, ViewChild, AfterViewChecked, ChangeDetectorRef } from '@angular/core'; 
import { RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http'; // 1. Import HttpClient

interface Message {
  text: string;
  isMe: boolean;
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

  // 2. Inject HttpClient in the constructor alongside ChangeDetectorRef
  constructor(
    private cdr: ChangeDetectorRef,
    private http: HttpClient
  ) {}

  sendMessage() {
    if (!this.newMessageText.trim()) return;

    const userQuery = this.newMessageText;

    // 3. Render your sent message bubble immediately
    this.messages.update(allMessages => [
      ...allMessages,
      { text: userQuery, isMe: true }
    ]);

    this.newMessageText = ''; // Clear input

    // 4. Send HTTP POST to your Spring Boot Server
    // Note: { responseType: 'text' } is crucial because your Java API returns raw text, not JSON!
    this.http.post('http://localhost:8080/api/ask', userQuery, { responseType: 'text' })
      .subscribe({
        next: (serverResponse) => {
          // Add the agent's real response to the chat!
          this.messages.update(allMessages => [
            ...allMessages,
            { text: serverResponse, isMe: false }
          ]);
          this.cdr.detectChanges(); // Force repaint
        },
        error: (err) => {
          console.error("Connection failed!", err);
          this.messages.update(allMessages => [
            ...allMessages,
            { text: "Error: Could not reach the Gateway Server.", isMe: false }
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
    } catch (err) {
      // Container not ready yet
    }
  }
}